package com.mintynex.notifications.controller;

import com.mintynex.notifications.model.Notification;
import com.mintynex.notifications.repository.NotificationRepository;
import com.mintynex.users.model.User;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    // GET /api/notifications?page=0&size=20
    @GetMapping
    public ResponseEntity<Page<NotifResponse>> getNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                notificationRepository
                        .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                        .map(NotifResponse::from));
    }

    // GET /api/notifications/unread-count
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @AuthenticationPrincipal User user) {
        long count = notificationRepository.countByUserIdAndReadFalse(user.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    // PUT /api/notifications/read-all
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal User user) {
        notificationRepository.markAllRead(user.getId());
        return ResponseEntity.ok().build();
    }

    // ── Inline DTO ─────────────────────────────────────────

    @Data
    public static class NotifResponse {
        private Long id;
        private String type;
        private String message;
        private boolean read;
        private LocalDateTime createdAt;

        public static NotifResponse from(Notification n) {
            NotifResponse r = new NotifResponse();
            r.id = n.getId();
            r.type = n.getType().name();
            r.message = n.getMessage();
            r.read = n.isRead();
            r.createdAt = n.getCreatedAt();
            return r;
        }
    }
}
