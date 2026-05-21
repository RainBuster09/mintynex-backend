package com.mintynex.posts.model;

import lombok.*;

import java.io.Serializable;

/**
 * UNCHANGED — src/main/java/com/mintynex/posts/model/PostLikeId.java
 *
 * Composite key for PostLike (userId + postId).
 * Included here for completeness; no changes needed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostLikeId implements Serializable {
    private Long user;
    private Long post;
}
