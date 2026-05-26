package com.mintynex.trade.model;

import com.mintynex.users.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "trades", indexes = {
        @Index(name = "idx_trades_proposer", columnList = "proposer_id"),
        @Index(name = "idx_trades_receiver", columnList = "receiver_id"),
        @Index(name = "idx_trades_status",   columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposer_id", nullable = false)
    private User proposer;

    // nullable — open offer has no specific receiver
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    private User receiver;

    @Column(name = "proposer_card", nullable = false, length = 200)
    private String proposerCard;

    @Column(name = "receiver_card", length = 200)
    private String receiverCard;

    @Column(name = "meetup_location", length = 300)
    private String meetupLocation;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public enum Status { PENDING, ACCEPTED, REJECTED, COMPLETED, FLAGGED }
}