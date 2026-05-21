package com.mintynex.binder.model;

import com.mintynex.users.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MODIFIED — src/main/java/com/mintynex/binder/model/BinderCard.java
 *
 * This entity was empty in the archive (failed RAR extraction).
 * Reconstructed to match existing BinderController field usage AND
 * the new spec (image upload via Supabase Storage, estimatedValue).
 *
 * Existing fields preserved: cardName, cardType, cardSet, imageUrl, grade,
 * gradeCompany, notes, addedAt, user.
 * New fields added: estimatedValue (BigDecimal).
 */
@Entity
@Table(name = "binder_cards", indexes = {
        @Index(name = "idx_binder_cards_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BinderCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "card_name", nullable = false, length = 150)
    private String cardName;

    @Column(name = "card_type", length = 80)
    private String cardType;

    @Column(name = "card_set", length = 120)
    private String cardSet;

    /** Public URL from Supabase Storage bucket "card-images" */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** e.g. "PSA 10", "BGS 9.5", "Raw" */
    @Column(length = 40)
    private String grade;

    /** e.g. "PSA", "BGS", "CGC" */
    @Column(name = "grade_company", length = 40)
    private String gradeCompany;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Estimated market value in USD (nullable) */
    @Column(name = "estimated_value", precision = 10, scale = 2)
    private BigDecimal estimatedValue;

    @Column(name = "added_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();
}
