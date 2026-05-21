package com.mintynex.auth.service;

import com.mintynex.auth.dto.AuthDto;
import com.mintynex.auth.model.OtpCode;
import com.mintynex.auth.model.RefreshToken;
import com.mintynex.auth.repository.OtpCodeRepository;
import com.mintynex.auth.repository.RefreshTokenRepository;
import com.mintynex.exception.BadRequestException;
import com.mintynex.exception.ConflictException;
import com.mintynex.exception.NotFoundException;
import com.mintynex.security.JwtUtils;
import com.mintynex.users.model.User;
import com.mintynex.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * MODIFIED — src/main/java/com/mintynex/auth/service/AuthService.java
 *
 * Changes:
 *  1. sendOtp() now returns MessageResponse containing [DEV CODE: xxxxxx] in the message.
 *  2. verifyOtp() now supports LOGIN purpose: looks up user by phone and returns tokens.
 *  3. verifyOtp() for RESET purpose returns a short-lived reset token instead of full auth.
 *  4. Password-based login is kept for backward compat but marked as admin/legacy use.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository         userRepository;
    private final OtpCodeRepository      otpCodeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtils               jwtUtils;
    private final PasswordEncoder        passwordEncoder;
    private final AuthenticationManager  authenticationManager;
    private final OtpService             otpService;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    // ── Register ──────────────────────────────────────────────────────────────
    @Transactional
    public AuthDto.MessageResponse register(AuthDto.RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername()))
            throw new ConflictException("Username already taken");
        if (userRepository.existsByEmail(req.getEmail()))
            throw new ConflictException("Email already registered");
        if (userRepository.existsByPhone(req.getPhone()))
            throw new ConflictException("Phone number already registered");

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .displayName(req.getUsername())
                .country(req.getCountry())
                .active(false) // activated after OTP verification
                .build();
        userRepository.save(user);

        String devCode = otpService.sendOtp(req.getPhone(), OtpCode.Purpose.REGISTER);

        return AuthDto.MessageResponse.builder()
                .message("Registration started. Check your phone for the OTP. [DEV CODE: " + devCode + "]")
                .build();
    }

    // ── Login (password-based — kept for admin/legacy use) ────────────────────
    @Transactional
    public AuthDto.AuthResponse login(AuthDto.LoginRequest req) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword())
        );
        User user = (User) auth.getPrincipal();
        return buildAuthResponse(user);
    }

    // ── Send OTP ──────────────────────────────────────────────────────────────
    @Transactional
    public AuthDto.MessageResponse sendOtp(AuthDto.SendOtpRequest req) {
        // For LOGIN and RESET, the user must already exist
        if (req.getPurpose() == OtpCode.Purpose.LOGIN || req.getPurpose() == OtpCode.Purpose.RESET) {
            User user = userRepository.findByPhone(req.getPhone())
                    .orElseThrow(() -> new NotFoundException("No account with that phone number"));
            // For LOGIN, user must be active
            if (req.getPurpose() == OtpCode.Purpose.LOGIN && !user.isActive()) {
                throw new BadRequestException("Account is not yet activated. Please complete registration first.");
            }
        }

        String devCode = otpService.sendOtp(req.getPhone(), req.getPurpose());

        return AuthDto.MessageResponse.builder()
                .message("OTP sent to " + req.getPhone() + ". [DEV CODE: " + devCode + "]")
                .build();
    }

    // ── Verify OTP ────────────────────────────────────────────────────────────
    @Transactional
    public AuthDto.AuthResponse verifyOtp(AuthDto.VerifyOtpRequest req) {
        OtpCode otp = otpCodeRepository
                .findLatestValid(req.getPhone(), req.getPurpose())
                .orElseThrow(() -> new BadRequestException("No valid OTP found. Request a new one."));

        if (otp.isExpired())
            throw new BadRequestException("OTP has expired. Request a new one.");
        if (!otp.getCode().equals(req.getCode()))
            throw new BadRequestException("Incorrect OTP code.");

        otp.setUsed(true);
        otpCodeRepository.save(otp);

        User user = userRepository.findByPhone(req.getPhone())
                .orElseThrow(() -> new NotFoundException("User not found"));

        switch (req.getPurpose()) {
            case REGISTER -> {
                // Activate account on successful REGISTER OTP
                user.setActive(true);
                user.setVerified(true);
                userRepository.save(user);
            }
            case LOGIN -> {
                // User must be active to log in via OTP
                if (!user.isActive()) {
                    throw new BadRequestException("Account is not active.");
                }
            }
            case RESET -> {
                // For RESET, return a short-lived reset token embedded in the access token field.
                // Frontend uses this token in POST /api/auth/reset-password instead of re-verifying OTP.
                String resetToken = jwtUtils.generateAccessToken(user); // short-lived (1hr)
                return AuthDto.AuthResponse.builder()
                        .accessToken(resetToken)
                        .refreshToken(null)
                        .user(null)
                        .build();
            }
        }

        return buildAuthResponse(user);
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────
    @Transactional
    public AuthDto.TokenResponse refresh(AuthDto.RefreshRequest req) {
        RefreshToken rt = refreshTokenRepository.findByToken(req.getRefreshToken())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (rt.isExpired()) {
            refreshTokenRepository.delete(rt);
            throw new BadRequestException("Session expired. Please log in again.");
        }

        String newAccessToken = jwtUtils.generateAccessToken(rt.getUser());
        return AuthDto.TokenResponse.builder().accessToken(newAccessToken).build();
    }

    // ── Reset Password ────────────────────────────────────────────────────────
    @Transactional
    public AuthDto.MessageResponse resetPassword(AuthDto.ResetPasswordRequest req) {
        OtpCode otp = otpCodeRepository
                .findLatestValid(req.getPhone(), OtpCode.Purpose.RESET)
                .orElseThrow(() -> new BadRequestException("No valid OTP. Request a new one."));

        if (otp.isExpired() || !otp.getCode().equals(req.getOtp()))
            throw new BadRequestException("Invalid or expired OTP.");

        otp.setUsed(true);
        otpCodeRepository.save(otp);

        User user = userRepository.findByPhone(req.getPhone())
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        return AuthDto.MessageResponse.builder()
                .message("Password reset successfully. You can now log in.")
                .build();
    }

    // ── Change Password (logged in) ────────────────────────────────────────────
    @Transactional
    public AuthDto.MessageResponse changePassword(User user, AuthDto.ChangePasswordRequest req) {
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash()))
            throw new BadRequestException("Current password is incorrect.");

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        refreshTokenRepository.deleteByUser(user);

        return AuthDto.MessageResponse.builder()
                .message("Password changed successfully.")
                .build();
    }

    // ── Logout ────────────────────────────────────────────────────────────────
    @Transactional
    public void logout(User user) {
        refreshTokenRepository.deleteByUser(user);
        log.info("User {} logged out", user.getUsername());
    }

    // ── Private Helper ────────────────────────────────────────────────────────
    private AuthDto.AuthResponse buildAuthResponse(User user) {
        userRepository.updateLastSeen(user.getId(), LocalDateTime.now());

        String accessToken  = jwtUtils.generateAccessToken(user);
        String refreshToken = jwtUtils.generateRefreshToken(user);

        refreshTokenRepository.deleteByUser(user);
        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiryMs / 1000))
                .build();
        refreshTokenRepository.save(rt);

        return AuthDto.AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(AuthDto.UserInfo.from(user))
                .build();
    }
}
