package com.mintynex.users.controller;

import com.mintynex.auth.dto.AuthDto;
import com.mintynex.exception.NotFoundException;
import com.mintynex.storage.SupabaseStorageService;
import com.mintynex.users.model.User;
import com.mintynex.users.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * MODIFIED — src/main/java/com/mintynex/users/controller/UserController.java
 *
 * Changes:
 *  - Added POST /api/users/me/avatar — upload avatar image to Supabase Storage
 *    bucket "profile-avatars", store public URL in user.avatarUrl.
 *  - Added POST /api/users/me/banner — upload banner image to Supabase Storage
 *    bucket "profile-banners", store public URL in user.bannerUrl.
 *  - GET /api/users/me and PUT /api/users/me are unchanged (already return full profile).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository         userRepository;
    private final SupabaseStorageService storageService;

    private static final String BUCKET_AVATARS = "profile-avatars";
    private static final String BUCKET_BANNERS = "profile-banners";

    // ── GET /api/users/me ─────────────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<AuthDto.UserInfo> getMe(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(AuthDto.UserInfo.from(user));
    }

    // ── GET /api/users/{id} ───────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<AuthDto.UserInfo> getUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return ResponseEntity.ok(AuthDto.UserInfo.from(user));
    }

    // ── PUT /api/users/me ─────────────────────────────────────────────────────
    @PutMapping("/me")
    public ResponseEntity<AuthDto.UserInfo> updateMe(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest req) {
        if (req.getDisplayName() != null) user.setDisplayName(req.getDisplayName());
        if (req.getBio()         != null) user.setBio(req.getBio());
        if (req.getCity()        != null) user.setCity(req.getCity());
        if (req.getCountry()     != null) user.setCountry(req.getCountry());
        // avatarUrl / bannerUrl can still be updated via text (e.g. social profile pic URL)
        if (req.getAvatarUrl()   != null) user.setAvatarUrl(req.getAvatarUrl());
        if (req.getBannerUrl()   != null) user.setBannerUrl(req.getBannerUrl());
        userRepository.save(user);
        return ResponseEntity.ok(AuthDto.UserInfo.from(user));
    }

    // ── POST /api/users/me/avatar (multipart) ─────────────────────────────────
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file == null || file.isEmpty())
            return ResponseEntity.badRequest().build();

        String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        // Use userId as filename prefix so re-uploads overwrite the old avatar
        String filename = "avatar-" + user.getId() + "-" + SupabaseStorageService.uniqueFilename(file.getOriginalFilename());
        String publicUrl = storageService.upload(file.getBytes(), BUCKET_AVATARS, filename, contentType);

        user.setAvatarUrl(publicUrl);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("avatarUrl", publicUrl));
    }

    // ── POST /api/users/me/banner (multipart) ─────────────────────────────────
    @PostMapping(value = "/me/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadBanner(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file == null || file.isEmpty())
            return ResponseEntity.badRequest().build();

        String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
        String filename = "banner-" + user.getId() + "-" + SupabaseStorageService.uniqueFilename(file.getOriginalFilename());
        String publicUrl = storageService.upload(file.getBytes(), BUCKET_BANNERS, filename, contentType);

        user.setBannerUrl(publicUrl);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("bannerUrl", publicUrl));
    }

    // ── DTO ───────────────────────────────────────────────────────────────────
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
