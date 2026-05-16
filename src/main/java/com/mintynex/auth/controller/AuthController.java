package com.mintynex.auth.controller;

import com.mintynex.auth.dto.AuthDto;
import com.mintynex.auth.service.AuthService;
import com.mintynex.users.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<AuthDto.MessageResponse> register(
            @Valid @RequestBody AuthDto.RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> login(
            @Valid @RequestBody AuthDto.LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    // POST /api/auth/send-otp
    @PostMapping("/send-otp")
    public ResponseEntity<AuthDto.MessageResponse> sendOtp(
            @Valid @RequestBody AuthDto.SendOtpRequest req) {
        return ResponseEntity.ok(authService.sendOtp(req));
    }

    // POST /api/auth/verify-otp
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthDto.AuthResponse> verifyOtp(
            @Valid @RequestBody AuthDto.VerifyOtpRequest req) {
        return ResponseEntity.ok(authService.verifyOtp(req));
    }

    // POST /api/auth/refresh
    @PostMapping("/refresh")
    public ResponseEntity<AuthDto.TokenResponse> refresh(
            @Valid @RequestBody AuthDto.RefreshRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    // POST /api/auth/reset-password
    @PostMapping("/reset-password")
    public ResponseEntity<AuthDto.MessageResponse> resetPassword(
            @Valid @RequestBody AuthDto.ResetPasswordRequest req) {
        return ResponseEntity.ok(authService.resetPassword(req));
    }

    // POST /api/auth/change-password  (requires login)
    @PostMapping("/change-password")
    public ResponseEntity<AuthDto.MessageResponse> changePassword(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AuthDto.ChangePasswordRequest req) {
        return ResponseEntity.ok(authService.changePassword(user, req));
    }

    // POST /api/auth/logout  (requires login)
    @PostMapping("/logout")
    public ResponseEntity<AuthDto.MessageResponse> logout(
            @AuthenticationPrincipal User user) {
        authService.logout(user);
        return ResponseEntity.ok(AuthDto.MessageResponse.builder()
                .message("Logged out successfully.").build());
    }
}
