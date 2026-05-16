package com.mintynex.posts.controller;

import com.mintynex.exception.NotFoundException;
import com.mintynex.notifications.model.Notification;
import com.mintynex.notifications.repository.NotificationRepository;
import com.mintynex.posts.model.Comment;
import com.mintynex.posts.model.Post;
import com.mintynex.posts.model.PostLike;
import com.mintynex.posts.model.PostLikeId;
import com.mintynex.posts.repository.CommentRepository;
import com.mintynex.posts.repository.PostLikeRepository;
import com.mintynex.posts.repository.PostRepository;
import com.mintynex.users.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostRepository         postRepository;
    private final CommentRepository      commentRepository;
    private final NotificationRepository notificationRepository;
    private final PostLikeRepository     postLikeRepository;

    // GET /api/posts?page=0&size=20
    @GetMapping
    public ResponseEntity<Page<PostResponse>> getFeed(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                postRepository.findAllByOrderByCreatedAtDesc(pageable).map(PostResponse::from));
    }

    // POST /api/posts
    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreatePostRequest req) {
        Post post = Post.builder()
                .user(user)
                .caption(req.getCaption())
                .imageUrl(req.getImageUrl())
                .cardLabel(req.getCardLabel())
                .tags(req.getTags())
                .build();
        postRepository.save(post);
        return ResponseEntity.status(HttpStatus.CREATED).body(PostResponse.from(post));
    }

    // DELETE /api/posts/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        if (!post.getUser().getId().equals(user.getId()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        postRepository.delete(post);
        return ResponseEntity.noContent().build();
    }

    // POST /api/posts/{id}/like  — toggle like/unlike
    @PostMapping("/{id}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Post not found"));

        PostLikeId likeId = new PostLikeId(user.getId(), post.getId());
        boolean alreadyLiked = postLikeRepository.existsById(likeId);

        if (alreadyLiked) {
            postLikeRepository.deleteById(likeId);
            post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
        } else {
            postLikeRepository.save(PostLike.builder().user(user).post(post).build());
            post.setLikesCount(post.getLikesCount() + 1);
            // Notify post owner (not for self-likes)
            if (!post.getUser().getId().equals(user.getId())) {
                notificationRepository.save(Notification.builder()
                        .user(post.getUser())
                        .type(Notification.Type.LIKE)
                        .message(user.getUsername() + " liked your post.")
                        .build());
            }
        }
        postRepository.save(post);
        return ResponseEntity.ok(Map.of("likes", post.getLikesCount(), "liked", !alreadyLiked));
    }

    // GET /api/posts/{id}/comments
    @GetMapping("/{id}/comments")
    public ResponseEntity<Page<CommentResponse>> getComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!postRepository.existsById(id))
            throw new NotFoundException("Post not found");
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                commentRepository.findByPostIdOrderByCreatedAtAsc(id, pageable)
                                 .map(CommentResponse::from));
    }

    // POST /api/posts/{id}/comments
    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody AddCommentRequest req) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content(req.getContent())
                .build();
        commentRepository.save(comment);
        post.setCommentsCount(post.getCommentsCount() + 1);
        postRepository.save(post);
        // Notify post owner
        if (!post.getUser().getId().equals(user.getId())) {
            notificationRepository.save(Notification.builder()
                    .user(post.getUser())
                    .type(Notification.Type.COMMENT)
                    .message(user.getUsername() + " commented on your post.")
                    .build());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(comment));
    }

    // ── Inline DTOs ───────────────────────────────────────

    @Data
    public static class CreatePostRequest {
        private String caption;
        private String imageUrl;
        private String cardLabel;
        private String tags;
    }

    @Data
    public static class AddCommentRequest {
        @NotBlank private String content;
    }

    @Data
    public static class PostResponse {
        private Long id;
        private String username;
        private String avatarUrl;
        private String caption;
        private String imageUrl;
        private String cardLabel;
        private String tags;
        private int likesCount;
        private int commentsCount;
        private LocalDateTime createdAt;

        public static PostResponse from(Post p) {
            PostResponse r = new PostResponse();
            r.id           = p.getId();
            r.username     = p.getUser().getUsername();
            r.avatarUrl    = p.getUser().getAvatarUrl();
            r.caption      = p.getCaption();
            r.imageUrl     = p.getImageUrl();
            r.cardLabel    = p.getCardLabel();
            r.tags         = p.getTags();
            r.likesCount   = p.getLikesCount();
            r.commentsCount= p.getCommentsCount();
            r.createdAt    = p.getCreatedAt();
            return r;
        }
    }

    @Data
    public static class CommentResponse {
        private Long id;
        private String username;
        private String avatarUrl;
        private String content;
        private LocalDateTime createdAt;

        public static CommentResponse from(Comment c) {
            CommentResponse r = new CommentResponse();
            r.id        = c.getId();
            r.username  = c.getUser().getUsername();
            r.avatarUrl = c.getUser().getAvatarUrl();
            r.content   = c.getContent();
            r.createdAt = c.getCreatedAt();
            return r;
        }
    }
}
