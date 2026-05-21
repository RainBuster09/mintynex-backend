package com.mintynex.posts.model;

import com.mintynex.users.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MODIFIED — src/main/java/com/mintynex/posts/model/Post.java
 *
 * Changes:
 *  - Added mediaUrl (String) to store Supabase public URL for uploaded image/video.
 *  - Added mediaType (enum IMAGE/VIDEO) to track the kind of media.
 *  - Kept imageUrl as a legacy alias — it is mapped to the same column as mediaUrl
 *    so existing data is not broken. Use mediaUrl going forward.
 *  - Added updatedAt field with @PreUpdate hook.
 */
@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_user_id",    columnList = "user_id"),
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

    /**
     * Public URL of the uploaded media file in Supabase Storage.
     * Column name kept as image_url for backward compatibility.
     */
    @Column(name = "image_url", length = 500)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 10)
    private MediaType mediaType;

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

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Bidirectional — used to delete orphaned likes/comments on cascade
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PostLike> likes = new ArrayList<>();

    public enum MediaType { IMAGE, VIDEO }

    // ── Convenience getter for legacy code that used imageUrl ────────────────
    public String getImageUrl() {
        return mediaUrl;
    }
}
