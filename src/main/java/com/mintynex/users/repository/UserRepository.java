package com.mintynex.users.repository;

import com.mintynex.users.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ── Lookup ────────────────────────────────────────────────────────────────

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    /** Used by UserDetailsServiceImpl — login via username OR email */
    Optional<User> findByUsernameOrEmail(String username, String email);

    // ── Existence checks ──────────────────────────────────────────────────────

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    // ── Search ────────────────────────────────────────────────────────────────

    /** Used by UserController — search by username prefix, case-insensitive */
    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT(:q, '%')) OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<User> searchByUsername(@Param("q") String q);
}