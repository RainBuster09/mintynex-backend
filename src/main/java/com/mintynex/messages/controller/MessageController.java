package com.mintynex.messages.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mintynex.exception.NotFoundException;
import com.mintynex.messages.model.Message;
import com.mintynex.messages.repository.MessageRepository;
import com.mintynex.notifications.model.Notification;
import com.mintynex.notifications.repository.NotificationRepository;
import com.mintynex.users.model.User;
import com.mintynex.users.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageRepository      messageRepository;
    private final UserRepository         userRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper           objectMapper = new ObjectMapper();

    // ── GET /api/messages/inbox ──────────────────────────────
    // Returns the latest message per conversation partner (inbox view)
    @GetMapping("/inbox")
    public ResponseEntity<List<ConversationSummary>> getInbox(
            @AuthenticationPrincipal User me) {
        List<Message> lastMessages = messageRepository.findInboxForUser(me.getId());
        List<ConversationSummary> inbox = lastMessages.stream().map(m -> {
            User partner = m.getSender().getId().equals(me.getId()) ? m.getReceiver() : m.getSender();
            long unread = messageRepository.countBySenderIdAndReceiverIdAndReadFalse(partner.getId(), me.getId());
            return ConversationSummary.from(partner, m, unread);
        }).toList();
        return ResponseEntity.ok(inbox);
    }

    // ── GET /api/messages/{userId}?page=0&size=50 ────────────
    @GetMapping("/{userId}")
    public ResponseEntity<Page<MessageResponse>> getConversation(
            @AuthenticationPrincipal User me,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MessageResponse> result = messageRepository
                .findConversation(me.getId(), userId, pageable)
                .map(m -> MessageResponse.from(m, me.getId()));
        return ResponseEntity.ok(result);
    }

    // ── POST /api/messages/{userId} ──────────────────────────
    @PostMapping("/{userId}")
    public ResponseEntity<MessageResponse> sendMessage(
            @AuthenticationPrincipal User me,
            @PathVariable Long userId,
            @RequestBody SendMessageRequest req) {
        User receiver = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Message msg = Message.builder()
                .sender(me)
                .receiver(receiver)
                .content(req.getContent())
                .build();
        messageRepository.save(msg);
        notificationRepository.save(Notification.builder()
                .user(receiver)
                .type(Notification.Type.MESSAGE)
                .message(me.getUsername() + " sent you a message.")
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(msg, me.getId()));
    }

    // ── PUT /api/messages/{msgId}/edit ───────────────────────
    // BUG FIX: Edit message — was completely missing from backend
    @PutMapping("/{msgId}/edit")
    @Transactional
    public ResponseEntity<MessageResponse> editMessage(
            @AuthenticationPrincipal User me,
            @PathVariable Long msgId,
            @RequestBody EditMessageRequest req) {
        Message msg = messageRepository.findById(msgId)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        if (!msg.getSender().getId().equals(me.getId()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        msg.setContent(req.getContent());
        msg.setEdited(true);
        msg.setEditedAt(LocalDateTime.now());
        messageRepository.save(msg);
        return ResponseEntity.ok(MessageResponse.from(msg, me.getId()));
    }

    // ── POST /api/messages/{msgId}/react ─────────────────────
    // BUG FIX: React to message — was completely missing from backend
    // Toggle reaction: add if not present, remove if already reacted with same emoji
    @PostMapping("/{msgId}/react")
    @Transactional
    public ResponseEntity<MessageResponse> reactToMessage(
            @AuthenticationPrincipal User me,
            @PathVariable Long msgId,
            @RequestBody ReactRequest req) {
        Message msg = messageRepository.findById(msgId)
                .orElseThrow(() -> new NotFoundException("Message not found"));

        // Only participants of the conversation can react
        boolean isParticipant = msg.getSender().getId().equals(me.getId())
                              || msg.getReceiver().getId().equals(me.getId());
        if (!isParticipant)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        // Parse existing reactions JSON
        Map<String, List<String>> reactions = parseReactions(msg.getReactions());
        String emoji  = req.getEmoji();
        String userId = String.valueOf(me.getId());

        List<String> users = reactions.computeIfAbsent(emoji, k -> new ArrayList<>());
        if (users.contains(userId)) {
            users.remove(userId);                // toggle off
            if (users.isEmpty()) reactions.remove(emoji);
        } else {
            users.add(userId);                   // toggle on
        }

        msg.setReactions(serializeReactions(reactions));
        messageRepository.save(msg);
        return ResponseEntity.ok(MessageResponse.from(msg, me.getId()));
    }

    // ── DELETE /api/messages/{msgId} ─────────────────────────
    @DeleteMapping("/{msgId}")
    @Transactional
    public ResponseEntity<Void> deleteMessage(
            @AuthenticationPrincipal User me,
            @PathVariable Long msgId) {
        Message msg = messageRepository.findById(msgId)
                .orElseThrow(() -> new NotFoundException("Message not found"));
        if (!msg.getSender().getId().equals(me.getId()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        messageRepository.delete(msg);
        return ResponseEntity.noContent().build();
    }

    // ── PUT /api/messages/{userId}/read ──────────────────────
    @PutMapping("/{userId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal User me,
            @PathVariable Long userId) {
        messageRepository.markAsRead(userId, me.getId());
        return ResponseEntity.ok().build();
    }

    // ── Helpers ──────────────────────────────────────────────

    private Map<String, List<String>> parseReactions(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json,
                    new TypeReference<Map<String, List<String>>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String serializeReactions(Map<String, List<String>> reactions) {
        try { return objectMapper.writeValueAsString(reactions); }
        catch (Exception e) { return "{}"; }
    }

    // ── DTOs ─────────────────────────────────────────────────

    @Data
    public static class SendMessageRequest {
        @NotBlank @Size(max = 4000) private String content;
    }

    @Data
    public static class EditMessageRequest {
        @NotBlank @Size(max = 4000) private String content;
    }

    @Data
    public static class ReactRequest {
        @NotBlank private String emoji;
    }

    @Data
    public static class MessageResponse {
        private Long id;
        private Long senderId;
        private String senderUsername;
        private String senderAvatarUrl;
        private Long receiverId;
        private String content;
        private boolean read;
        private boolean edited;
        private LocalDateTime editedAt;
        private LocalDateTime createdAt;
        private Map<String, List<String>> reactions;
        private boolean mine;   // convenience flag for frontend

        public static MessageResponse from(Message m, Long currentUserId) {
            MessageResponse r = new MessageResponse();
            r.id              = m.getId();
            r.senderId        = m.getSender().getId();
            r.senderUsername  = m.getSender().getUsername();
            r.senderAvatarUrl = m.getSender().getAvatarUrl();
            r.receiverId      = m.getReceiver().getId();
            r.content         = m.getContent();
            r.read            = m.isRead();
            r.edited          = m.isEdited();
            r.editedAt        = m.getEditedAt();
            r.createdAt       = m.getCreatedAt();
            r.mine            = m.getSender().getId().equals(currentUserId);
            // Parse reactions JSON
            try {
                r.reactions = new ObjectMapper().readValue(
                        m.getReactions() != null ? m.getReactions() : "{}",
                        new TypeReference<Map<String, List<String>>>() {});
            } catch (Exception e) {
                r.reactions = new LinkedHashMap<>();
            }
            return r;
        }
    }

    @Data
    public static class ConversationSummary {
        private Long partnerId;
        private String partnerUsername;
        private String partnerAvatarUrl;
        private String lastMessage;
        private LocalDateTime lastMessageAt;
        private long unreadCount;

        public static ConversationSummary from(User partner, Message last, long unread) {
            ConversationSummary s = new ConversationSummary();
            s.partnerId         = partner.getId();
            s.partnerUsername   = partner.getUsername();
            s.partnerAvatarUrl  = partner.getAvatarUrl();
            s.lastMessage       = last.getContent();
            s.lastMessageAt     = last.getCreatedAt();
            s.unreadCount       = unread;
            return s;
        }
    }
}
