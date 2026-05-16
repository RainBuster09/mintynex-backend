package com.mintynex.posts.model;

import com.mintynex.users.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_user_id", columnList = "user_id"),
        @Index(name = "idx_posts_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "card_label", length = 120)
    private String cardLabel;

    @Column(length = 500)
    private String tags;

    @Column(name = "likes_count", nullable = false)
    @Builder.Default
    private int likesCount = 0;

    @Column(name = "comments_count", nullable = false)
    @Builder.Default
    private int commentsCount = 0;

    @Column(name = "shares_count", nullable = false)
    @Builder.Default
    private int sharesCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // Bidirectional — used to delete orphaned likes/comments
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostLike> likes = new ArrayList<>();
}
