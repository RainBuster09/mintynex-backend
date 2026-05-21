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
import com.mintynex.storage.SupabaseStorageService;
import com.mintynex.users.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * MODIFIED — src/main/java/com/mintynex/posts/controller/PostController.java
 *
 * Changes:
 *  - createPost() now accepts multipart/form-data: caption (text field) + optional file.
 *    File is uploaded to Supabase Storage bucket "post-media"; URL stored in mediaUrl.
 *  - getFeed() now returns enriched PostResponse with likedByMe, shareCount, author fields.
 *  - Added POST /api/posts/{id}/share — increments shareCount.
 *  - PostResponse updated: mediaUrl, mediaType, sharesCount, likedByMe, author info.
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostRepository         postRepository;
    private final CommentRepository      commentRepository;
    private final NotificationRepository notificationRepository;
    private final PostLikeRepository     postLikeRepository;
    private final SupabaseStorageService storageService;

    private static final String BUCKET_POST_MEDIA = "post-media";

    // ── GET /api/posts?page=0&size=20 ────────────────────────────────────────
    @GetMapping
    public ResponseEntity<Page<PostResponse>> getFeed(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Long userId = currentUser != null ? currentUser.getId() : null;

        Page<PostResponse> feed = postRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(post -> PostResponse.from(post, userId, postRepository));

        return ResponseEntity.ok(feed);
    }

    // ── POST /api/posts (multipart) ───────────────────────────────────────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal User user,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "cardLabel", required = false) String cardLabel,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {

        String mediaUrl  = null;
        Post.MediaType mediaType = null;

        if (file != null && !file.isEmpty()) {
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            String filename    = SupabaseStorageService.uniqueFilename(file.getOriginalFilename());
            mediaUrl  = storageService.upload(file.getBytes(), BUCKET_POST_MEDIA, filename, contentType);
            mediaType = contentType.startsWith("video/") ? Post.MediaType.VIDEO : Post.MediaType.IMAGE;
        }

        Post post = Post.builder()
                .user(user)
                .caption(caption)
                .mediaUrl(mediaUrl)
                .mediaType(mediaType)
                .cardLabel(cardLabel)
                .tags(tags)
                .build();
        postRepository.save(post);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PostResponse.from(post, user.getId(), postRepository));
    }

    // ── DELETE /api/posts/{id} ────────────────────────────────────────────────
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

    // ── POST /api/posts/{id}/like — toggle like/unlike ────────────────────────
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
            if (!post.getUser().getId().equals(user.getId())) {
                notificationRepository.save(Notification.builder()
                        .user(post.getUser())
                        .type(Notification.Type.LIKE)
                        .message(user.getUsername() + " liked your post.")
                        .build());
            }
        }
        postRepository.save(post);

        return ResponseEntity.ok(Map.of(
                "liked",     !alreadyLiked,
                "likeCount", post.getLikesCount()
        ));
    }

    // ── POST /api/posts/{id}/share — increment share count ───────────────────
    @PostMapping("/{id}/share")
    public ResponseEntity<Map<String, Object>> sharePost(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        post.setSharesCount(post.getSharesCount() + 1);
        postRepository.save(post);
        return ResponseEntity.ok(Map.of("shareCount", post.getSharesCount()));
    }

    // ── GET /api/posts/{id}/comments ─────────────────────────────────────────
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

    // ── POST /api/posts/{id}/comments ────────────────────────────────────────
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
        if (!post.getUser().getId().equals(user.getId())) {
            notificationRepository.save(Notification.builder()
                    .user(post.getUser())
                    .type(Notification.Type.COMMENT)
                    .message(user.getUsername() + " commented on your post.")
                    .build());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(comment));
    }

    // ── Inline DTOs ───────────────────────────────────────────────────────────

    @Data
    public static class AddCommentRequest {
        @NotBlank private String content;
    }

    @Data
    public static class AuthorInfo {
        private Long   id;
        private String username;
        private String avatarUrl;
        private String rank;
        private boolean verified;

        public static AuthorInfo from(User u) {
            AuthorInfo a = new AuthorInfo();
            a.id       = u.getId();
            a.username = u.getUsername();
            a.avatarUrl= u.getAvatarUrl();
            a.rank     = u.getRank().name();
            a.verified = u.isVerified();
            return a;
        }
    }

    @Data
    public static class PostResponse {
        private Long       id;
        private AuthorInfo author;
        private String     caption;
        private String     mediaUrl;
        private String     mediaType;
        private String     cardLabel;
        private String     tags;
        private int        likesCount;
        private int        commentsCount;
        private int        sharesCount;
        private boolean    likedByMe;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static PostResponse from(Post p, Long currentUserId, PostRepository repo) {
            PostResponse r = new PostResponse();
            r.id           = p.getId();
            r.author       = AuthorInfo.from(p.getUser());
            r.caption      = p.getCaption();
            r.mediaUrl     = p.getMediaUrl();
            r.mediaType    = p.getMediaType() != null ? p.getMediaType().name() : null;
            r.cardLabel    = p.getCardLabel();
            r.tags         = p.getTags();
            r.likesCount   = p.getLikesCount();
            r.commentsCount= p.getCommentsCount();
            r.sharesCount  = p.getSharesCount();
            r.likedByMe    = currentUserId != null && repo.isLikedByUser(currentUserId, p.getId());
            r.createdAt    = p.getCreatedAt();
            r.updatedAt    = p.getUpdatedAt();
            return r;
        }
    }

    @Data
    public static class CommentResponse {
        private Long   id;
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
