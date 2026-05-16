package com.mintynex.posts.model;

import java.io.Serializable;
import java.util.Objects;

public class PostLikeId implements Serializable {
    private Long user;
    private Long post;

    public PostLikeId() {}
    public PostLikeId(Long user, Long post) { this.user = user; this.post = post; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostLikeId id)) return false;
        return Objects.equals(user, id.user) && Objects.equals(post, id.post);
    }
    @Override public int hashCode() { return Objects.hash(user, post); }
}
