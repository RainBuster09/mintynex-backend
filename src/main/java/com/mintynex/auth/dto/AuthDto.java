package com.mintynex.auth.dto;

import com.mintynex.auth.model.OtpCode;
import com.mintynex.users.model.User;
import jakarta.validation.constraints.*;
import lombok.*;

public class AuthDto {

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 40, message = "Username must be 3-40 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Only letters, numbers and underscores allowed")
        private String username;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        private String email;

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+[1-9]\\d{6,19}$", message = "Phone must be E.164 format e.g. +254712345678")
        private String phone;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        private String password;

        @Size(max = 60)
        private String country;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Username or email is required")
        private String username;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    public static class SendOtpRequest {
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+[1-9]\\d{6,19}$", message = "Phone must be E.164 format")
        private String phone;

        @NotNull(message = "Purpose is required")
        private OtpCode.Purpose purpose;
    }

    @Data
    public static class VerifyOtpRequest {
        @NotBlank(message = "Phone is required")
        private String phone;

        @NotBlank(message = "OTP code is required")
        @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
        private String code;

        @NotNull(message = "Purpose is required")
        private OtpCode.Purpose purpose;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank(message = "Refresh token is required")
        private String refreshToken;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "Phone is required")
        private String phone;

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be exactly 6 digits")
        private String otp;

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        private String newPassword;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "Current password is required")
        private String currentPassword;

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        private String newPassword;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private UserInfo user;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String phone;
        private String displayName;
        private String avatarUrl;
        private String bannerUrl;
        private String bio;
        private String country;
        private String city;
        private String rank;
        private boolean verified;
        private boolean premium;
        private String premiumPlan;
        private String role;

        public static UserInfo from(User u) {
            return UserInfo.builder()
                    .id(u.getId())
                    .username(u.getUsername())
                    .email(u.getEmail())
                    .phone(u.getPhone())
                    .displayName(u.getDisplayName())
                    .avatarUrl(u.getAvatarUrl())
                    .bannerUrl(u.getBannerUrl())
                    .bio(u.getBio())
                    .country(u.getCountry())
                    .city(u.getCity())
                    .rank(u.getRank().name())
                    .verified(u.isVerified())
                    .premium(u.isPremium())
                    .premiumPlan(u.getPremiumPlan().name())
                    .role(u.getRole().name())
                    .build();
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TokenResponse {
        private String accessToken;
    }
}
