package com.mintynex.binder.model;

import com.mintynex.users.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "binder_cards", indexes = {
        @Index(name = "idx_binder_user_id", columnList = "user_id")
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

    @Column(name = "card_type", length = 50)
    private String cardType;

    @Column(name = "card_set", length = 100)
    private String cardSet;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 20)
    private String grade;

    @Column(name = "grade_company", length = 20)
    private String gradeCompany;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "added_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();
}
