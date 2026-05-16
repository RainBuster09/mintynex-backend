package com.mintynex.auth.repository;

import com.mintynex.auth.model.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    // Latest unused, unexpired OTP for a phone + purpose
    @Query("SELECT o FROM OtpCode o WHERE o.phone = :phone AND o.purpose = :purpose " +
           "AND o.used = false ORDER BY o.createdAt DESC LIMIT 1")
    Optional<OtpCode> findLatestValid(
            @Param("phone") String phone,
            @Param("purpose") OtpCode.Purpose purpose
    );

    // Invalidate old codes for the same phone + purpose before sending a new one
    @Modifying
    @Query("UPDATE OtpCode o SET o.used = true WHERE o.phone = :phone AND o.purpose = :purpose")
    void invalidateAll(
            @Param("phone") String phone,
            @Param("purpose") OtpCode.Purpose purpose
    );
}
