package com.mintynex.users.repository;

import com.mintynex.users.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    // Used by UserDetailsService — login with username OR email
    Optional<User> findByUsernameOrEmail(String username, String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    // Admin: search users by username or email
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<User> searchUsers(@Param("q") String query, Pageable pageable);

    // Players: find by country/city
    Page<User> findByCountryIgnoreCaseAndActiveTrue(String country, Pageable pageable);

    // Online count: users seen in last 5 minutes
    @Query("SELECT COUNT(u) FROM User u WHERE u.lastSeenAt > :since AND u.active = true")
    long countOnlineSince(@Param("since") LocalDateTime since);

    // Update last seen timestamp
    @Modifying
    @Query("UPDATE User u SET u.lastSeenAt = :now WHERE u.id = :id")
    void updateLastSeen(@Param("id") Long id, @Param("now") LocalDateTime now);
}
