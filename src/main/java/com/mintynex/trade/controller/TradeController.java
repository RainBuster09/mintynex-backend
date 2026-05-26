package com.mintynex.trade.controller;

import com.mintynex.exception.BadRequestException;
import com.mintynex.exception.NotFoundException;
import com.mintynex.notifications.model.Notification;
import com.mintynex.notifications.repository.NotificationRepository;
import com.mintynex.trade.model.Trade;
import com.mintynex.trade.repository.TradeRepository;
import com.mintynex.users.model.User;
import com.mintynex.users.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * TradeController — /api/trades
 *
 * Endpoints:
 *   GET    /api/trades                     — list all trades for current user (as proposer or receiver)
 *   POST   /api/trades                     — propose a new trade
 *   PUT    /api/trades/{id}/accept         — receiver accepts
 *   PUT    /api/trades/{id}/reject         — receiver rejects
 *   PUT    /api/trades/{id}/complete       — either party marks complete
 *   POST   /api/trades/{id}/flag           — flag a trade for dispute
 */
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeRepository        tradeRepository;
    private final UserRepository         userRepository;
    private final NotificationRepository notificationRepository;

    // ── GET /api/trades ──────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<List<TradeResponse>> listTrades(
            @AuthenticationPrincipal User user) {
        List<Trade> trades = tradeRepository.findByProposerIdOrReceiverIdOrderByCreatedAtDesc(
                user.getId(), user.getId());
        return ResponseEntity.ok(trades.stream().map(t -> TradeResponse.from(t, user.getId())).toList());
    }

    // ── POST /api/trades ─────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<TradeResponse> proposeTrade(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ProposeTradeRequest req) {

        // receiver is optional — if not supplied the trade is an open offer
        User receiver = null;
        if (req.getReceiverId() != null) {
            receiver = userRepository.findById(req.getReceiverId())
                    .orElseThrow(() -> new NotFoundException("Receiver not found"));
            if (receiver.getId().equals(user.getId()))
                throw new BadRequestException("Cannot trade with yourself");
        }

        Trade trade = Trade.builder()
                .proposer(user)
                .receiver(receiver)
                .proposerCard(req.getProposerCard())
                .receiverCard(req.getReceiverCard())
                .meetupLocation(req.getMeetupLocation())
                .message(req.getMessage())
                .status(Trade.Status.PENDING)
                .build();

        tradeRepository.save(trade);

        // Notify receiver
        if (receiver != null) {
            Notification notif = Notification.builder()
                    .user(receiver)
                    .type(Notification.Type.TRADE_OFFER)
                    .message(user.getUsername() + " proposed a trade: " + req.getProposerCard())
                    .build();
            notificationRepository.save(notif);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(TradeResponse.from(trade, user.getId()));
    }

    // ── PUT /api/trades/{id}/accept ──────────────────────────────────────────
    @PutMapping("/{id}/accept")
    public ResponseEntity<TradeResponse> acceptTrade(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Trade trade = findAndAuthorize(id, user, "accept");
        if (trade.getStatus() != Trade.Status.PENDING)
            throw new BadRequestException("Trade is not pending");

        trade.setStatus(Trade.Status.ACCEPTED);
        trade.setUpdatedAt(LocalDateTime.now());
        tradeRepository.save(trade);

        // Notify proposer
        Notification notif = Notification.builder()
                .user(trade.getProposer())
                .type(Notification.Type.TRADE_ACCEPTED)
                .message(user.getUsername() + " accepted your trade offer!")
                .build();
        notificationRepository.save(notif);

        return ResponseEntity.ok(TradeResponse.from(trade, user.getId()));
    }

    // ── PUT /api/trades/{id}/reject ──────────────────────────────────────────
    @PutMapping("/{id}/reject")
    public ResponseEntity<TradeResponse> rejectTrade(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Trade trade = findAndAuthorize(id, user, "reject");
        if (trade.getStatus() != Trade.Status.PENDING)
            throw new BadRequestException("Trade is not pending");

        trade.setStatus(Trade.Status.REJECTED);
        trade.setUpdatedAt(LocalDateTime.now());
        tradeRepository.save(trade);

        return ResponseEntity.ok(TradeResponse.from(trade, user.getId()));
    }

    // ── PUT /api/trades/{id}/complete ────────────────────────────────────────
    @PutMapping("/{id}/complete")
    public ResponseEntity<TradeResponse> completeTrade(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {

        Trade trade = tradeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Trade not found"));

        boolean isParty = trade.getProposer().getId().equals(user.getId()) ||
                (trade.getReceiver() != null && trade.getReceiver().getId().equals(user.getId()));
        if (!isParty) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        if (trade.getStatus() != Trade.Status.ACCEPTED)
            throw new BadRequestException("Trade must be accepted before completing");

        trade.setStatus(Trade.Status.COMPLETED);
        trade.setUpdatedAt(LocalDateTime.now());
        tradeRepository.save(trade);

        return ResponseEntity.ok(TradeResponse.from(trade, user.getId()));
    }

    // ── POST /api/trades/{id}/flag ───────────────────────────────────────────
    @PostMapping("/{id}/flag")
    public ResponseEntity<Map<String, String>> flagTrade(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody(required = false) FlagRequest req) {

        Trade trade = tradeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Trade not found"));

        boolean isParty = trade.getProposer().getId().equals(user.getId()) ||
                (trade.getReceiver() != null && trade.getReceiver().getId().equals(user.getId()));
        if (!isParty) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        trade.setStatus(Trade.Status.FLAGGED);
        trade.setUpdatedAt(LocalDateTime.now());
        tradeRepository.save(trade);

        return ResponseEntity.ok(Map.of("message", "Trade flagged for review"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private Trade findAndAuthorize(Long id, User user, String action) {
        Trade trade = tradeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Trade not found"));
        // only receiver can accept/reject
        if (trade.getReceiver() == null || !trade.getReceiver().getId().equals(user.getId()))
            throw new BadRequestException("Only the receiver can " + action + " this trade");
        return trade;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────
    @Data
    public static class ProposeTradeRequest {
        @NotBlank(message = "Your card is required")
        private String proposerCard;

        private String receiverCard;
        private Long   receiverId;
        private String meetupLocation;
        private String message;
    }

    @Data
    public static class FlagRequest {
        private String reason;
    }

    @Data
    public static class TradeResponse {
        private Long   id;
        private String status;
        private String role;          // "proposer" or "receiver"
        private Long   proposerId;
        private String proposerUsername;
        private String proposerAvatar;
        private Long   receiverId;
        private String receiverUsername;
        private String receiverAvatar;
        private String proposerCard;
        private String receiverCard;
        private String meetupLocation;
        private String message;
        private String createdAt;
        private String updatedAt;

        public static TradeResponse from(Trade t, Long currentUserId) {
            TradeResponse r = new TradeResponse();
            r.id               = t.getId();
            r.status           = t.getStatus().name();
            r.role             = t.getProposer().getId().equals(currentUserId) ? "proposer" : "receiver";
            r.proposerId       = t.getProposer().getId();
            r.proposerUsername = t.getProposer().getUsername();
            r.proposerAvatar   = t.getProposer().getAvatarUrl();
            if (t.getReceiver() != null) {
                r.receiverId       = t.getReceiver().getId();
                r.receiverUsername = t.getReceiver().getUsername();
                r.receiverAvatar   = t.getReceiver().getAvatarUrl();
            }
            r.proposerCard    = t.getProposerCard();
            r.receiverCard    = t.getReceiverCard();
            r.meetupLocation  = t.getMeetupLocation();
            r.message         = t.getMessage();
            r.createdAt       = t.getCreatedAt() != null ? t.getCreatedAt().toString() : null;
            r.updatedAt       = t.getUpdatedAt() != null ? t.getUpdatedAt().toString() : null;
            return r;
        }
    }
}