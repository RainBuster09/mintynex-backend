package com.mintynex.users.controller;

import com.mintynex.auth.dto.AuthDto;
import com.mintynex.exception.NotFoundException;
import com.mintynex.users.model.User;
import com.mintynex.users.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    // GET /api/users/me
    @GetMapping("/me")
    public ResponseEntity<AuthDto.UserInfo> getMe(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(AuthDto.UserInfo.from(user));
    }

    // GET /api/users/{id}
    @GetMapping("/{id}")
    public ResponseEntity<AuthDto.UserInfo> getUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return ResponseEntity.ok(AuthDto.UserInfo.from(user));
    }

    // PUT /api/users/me
    @PutMapping("/me")
    public ResponseEntity<AuthDto.UserInfo> updateMe(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest req) {
        if (req.getDisplayName() != null) user.setDisplayName(req.getDisplayName());
        if (req.getBio()         != null) user.setBio(req.getBio());
        if (req.getCity()        != null) user.setCity(req.getCity());
        if (req.getCountry()     != null) user.setCountry(req.getCountry());
        if (req.getAvatarUrl()   != null) user.setAvatarUrl(req.getAvatarUrl());
        if (req.getBannerUrl()   != null) user.setBannerUrl(req.getBannerUrl());
        userRepository.save(user);
        return ResponseEntity.ok(AuthDto.UserInfo.from(user));
    }

    // ── DTO ───────────────────────────────────────────────
    @Data
    public static class UpdateProfileRequest {
        @Size(max = 80)  private String displayName;
        @Size(max = 500) private String bio;
        @Size(max = 80)  private String city;
        @Size(max = 60)  private String country;
        @Size(max = 500) private String avatarUrl;
        @Size(max = 500) private String bannerUrl;
    }
}
