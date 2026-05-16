package com.mintynex.messages.controller;

import com.mintynex.exception.NotFoundException;
import com.mintynex.messages.model.Message;
import com.mintynex.messages.repository.MessageRepository;
import com.mintynex.notifications.model.Notification;
import com.mintynex.notifications.repository.NotificationRepository;
import com.mintynex.users.model.User;
import com.mintynex.users.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageRepository      messageRepository;
    private final UserRepository         userRepository;
    private final NotificationRepository notificationRepository;

    // GET /api/messages/{userId}?page=0&size=50
    @GetMapping("/{userId}")
    public ResponseEntity<Page<MessageResponse>> getConversation(
            @AuthenticationPrincipal User me,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                messageRepository.findConversation(me.getId(), userId, pageable)
                                 .map(MessageResponse::from));
    }

    // POST /api/messages/{userId}
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
        // Notify receiver
        notificationRepository.save(Notification.builder()
                .user(receiver)
                .type(Notification.Type.MESSAGE)
                .message(me.getUsername() + " sent you a message.")
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(msg));
    }

    // PUT /api/messages/{userId}/read
    @PutMapping("/{userId}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal User me,
            @PathVariable Long userId) {
        messageRepository.markAsRead(userId, me.getId());
        return ResponseEntity.ok().build();
    }

    // ── Inline DTOs ───────────────────────────────────────

    @Data
    public static class SendMessageRequest {
        @NotBlank private String content;
    }

    @Data
    public static class MessageResponse {
        private Long id;
        private Long senderId;
        private String senderUsername;
        private String content;
        private boolean read;
        private LocalDateTime createdAt;

        public static MessageResponse from(Message m) {
            MessageResponse r = new MessageResponse();
            r.id = m.getId();
            r.senderId = m.getSender().getId();
            r.senderUsername = m.getSender().getUsername();
            r.content = m.getContent();
            r.read = m.isRead();
            r.createdAt = m.getCreatedAt();
            return r;
        }
    }
}
