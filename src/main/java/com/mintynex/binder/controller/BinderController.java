package com.mintynex.binder.controller;

import com.mintynex.binder.model.BinderCard;
import com.mintynex.binder.repository.BinderCardRepository;
import com.mintynex.exception.NotFoundException;
import com.mintynex.storage.SupabaseStorageService;
import com.mintynex.users.model.User;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/binder")
@RequiredArgsConstructor
public class BinderController {

    private final BinderCardRepository binderCardRepository;
    private final SupabaseStorageService storageService;

    // ── GET /api/binder?page=0&size=18 ───────────────────────
    @GetMapping
    public ResponseEntity<Page<CardResponse>> getBinder(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "18") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                binderCardRepository
                        .findByUserIdOrderByAddedAtDesc(user.getId(), pageable)
                        .map(CardResponse::from));
    }

    // ── GET /api/binder/stats ─────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(@AuthenticationPrincipal User user) {
        long total    = binderCardRepository.countByUserId(user.getId());
        long psaCount = binderCardRepository.countByUserIdAndGradeCompany(user.getId(), "PSA");
        long bgsCount = binderCardRepository.countByUserIdAndGradeCompany(user.getId(), "BGS");
        return ResponseEntity.ok(Map.of(
                "total", total,
                "psa",   psaCount,
                "bgs",   bgsCount
        ));
    }

    // ── POST /api/binder  (JSON) ──────────────────────────────
    @PostMapping
    public ResponseEntity<CardResponse> addCard(
            @AuthenticationPrincipal User user,
            @RequestBody AddCardRequest req) {
        BinderCard card = BinderCard.builder()
                .user(user)
                .cardName(req.getCardName())
                .cardType(req.getCardType())
                .cardSet(req.getCardSet())
                .imageUrl(req.getImageUrl())
                .grade(req.getGrade())
                .gradeCompany(req.getGradeCompany())
                .notes(req.getNotes())
                .build();
        binderCardRepository.save(card);
        return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(card));
    }

    // ── POST /api/binder/upload-image ─────────────────────────
    // BUG FIX: Card image upload was missing
    @PostMapping("/upload-image")
    public ResponseEntity<Map<String, String>> uploadCardImage(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || !file.getContentType().startsWith("image/"))
            return ResponseEntity.badRequest().build();
        if (file.getSize() > 10 * 1024 * 1024)
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        String filename = "cards/" + user.getId() + "/" +
                SupabaseStorageService.uniqueFilename(file.getOriginalFilename());
        String url = storageService.upload(file.getBytes(), "card-images", filename, file.getContentType());
        return ResponseEntity.ok(Map.of("url", url));
    }

    // ── PUT /api/binder/{id} ──────────────────────────────────
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<CardResponse> updateCard(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody AddCardRequest req) {
        BinderCard card = binderCardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Card not found"));
        if (!card.getUser().getId().equals(user.getId()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (req.getCardName()     != null) card.setCardName(req.getCardName());
        if (req.getCardType()     != null) card.setCardType(req.getCardType());
        if (req.getCardSet()      != null) card.setCardSet(req.getCardSet());
        if (req.getImageUrl()     != null) card.setImageUrl(req.getImageUrl());
        if (req.getGrade()        != null) card.setGrade(req.getGrade());
        if (req.getGradeCompany() != null) card.setGradeCompany(req.getGradeCompany());
        if (req.getNotes()        != null) card.setNotes(req.getNotes());
        binderCardRepository.save(card);
        return ResponseEntity.ok(CardResponse.from(card));
    }

    // ── DELETE /api/binder/{id} ───────────────────────────────
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> removeCard(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        BinderCard card = binderCardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Card not found"));
        if (!card.getUser().getId().equals(user.getId()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        binderCardRepository.delete(card);
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ─────────────────────────────────────────────────

    @Data
    public static class AddCardRequest {
        private String cardName;
        private String cardType;
        private String cardSet;
        private String imageUrl;
        private String grade;
        private String gradeCompany;
        private String notes;
    }

    @Data
    public static class CardResponse {
        private Long id;
        private String cardName;
        private String cardType;
        private String cardSet;
        private String imageUrl;
        private String grade;
        private String gradeCompany;
        private String notes;
        private LocalDateTime addedAt;

        public static CardResponse from(BinderCard c) {
            CardResponse r = new CardResponse();
            r.id           = c.getId();
            r.cardName     = c.getCardName();
            r.cardType     = c.getCardType();
            r.cardSet      = c.getCardSet();
            r.imageUrl     = c.getImageUrl();
            r.grade        = c.getGrade();
            r.gradeCompany = c.getGradeCompany();
            r.notes        = c.getNotes();
            r.addedAt      = c.getAddedAt();
            return r;
        }
    }
}
