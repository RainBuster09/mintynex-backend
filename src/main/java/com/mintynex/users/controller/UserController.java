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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final SupabaseStorageService storageService;

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

    // GET /api/users/search?q=...
    @GetMapping("/search")
    public ResponseEntity<List<AuthDto.UserInfo>> searchUsers(
            @RequestParam(defaultValue = "") String q) {
        List<User> results = userRepository.searchByUsername(q.toLowerCase());
        return ResponseEntity.ok(results.stream().map(AuthDto.UserInfo::from).toList());
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

    // POST /api/users/avatar  — multipart upload
    @PostMapping("/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) throws IOException {

        validateImageFile(file);
        String filename = "avatars/" + user.getId() + "_" +
                SupabaseStorageService.uniqueFilename(file.getOriginalFilename());
        String url = storageService.upload(file.getBytes(), "profile-media", filename, file.getContentType());
        user.setAvatarUrl(url);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("url", url));
    }

    // POST /api/users/banner  — multipart upload (was MISSING — root cause of banner bug)
    @PostMapping("/banner")
    public ResponseEntity<Map<String, String>> uploadBanner(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) throws IOException {

        validateImageFile(file);
        String filename = "banners/" + user.getId() + "_" +
                SupabaseStorageService.uniqueFilename(file.getOriginalFilename());
        String url = storageService.upload(file.getBytes(), "profile-media", filename, file.getContentType());
        user.setBannerUrl(url);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("url", url));
    }

    // ── Helpers ──────────────────────────────────────────

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("File is empty");
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/"))
            throw new IllegalArgumentException("Only image files are accepted");
        if (file.getSize() > 20 * 1024 * 1024)
            throw new IllegalArgumentException("File too large (max 20 MB)");
    }

    // ── DTO ──────────────────────────────────────────────
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
