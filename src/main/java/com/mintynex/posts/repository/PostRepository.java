package com.mintynex.posts.repository;

import com.mintynex.posts.model.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Post> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT COUNT(pl) > 0 FROM PostLike pl WHERE pl.user.id = :userId AND pl.post.id = :postId")
    boolean isLikedByUser(@Param("userId") Long userId, @Param("postId") Long postId);
}
