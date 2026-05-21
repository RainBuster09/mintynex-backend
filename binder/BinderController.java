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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MODIFIED — src/main/java/com/mintynex/binder/controller/BinderController.java
 *
 * Changes:
 *  - POST /api/binder now accepts multipart/form-data: cardName, cardType, cardSet,
 *    grade, gradeCompany, notes, estimatedValue (all text fields) + optional file (image).
 *    The image is uploaded to Supabase Storage bucket "card-images" and the public URL
 *    is stored in imageUrl.
 *  - GET /api/binder — unchanged except CardResponse now includes estimatedValue.
 *  - DELETE /api/binder/{id} — unchanged.
 *
 * Frontend migration note:
 *  Old JSON body: { cardName, cardType, cardSet, imageUrl, grade, gradeCompany, notes }
 *  New multipart:  cardName=..., file=<binary>  (imageUrl no longer accepted as plain text)
 *  To keep backward compat, if no file is supplied, imageUrl text param is accepted too.
 */
@RestController
@RequestMapping("/api/binder")
@RequiredArgsConstructor
public class BinderController {

    private final BinderCardRepository   binderCardRepository;
    private final SupabaseStorageService storageService;

    private static final String BUCKET_CARD_IMAGES = "card-images";

    // ── GET /api/binder?page=0&size=18 ───────────────────────────────────────
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

    // ── POST /api/binder (multipart) ─────────────────────────────────────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CardResponse> addCard(
            @AuthenticationPrincipal User user,
            @RequestParam("cardName")                         String cardName,
            @RequestParam(value = "cardType",      required = false) String cardType,
            @RequestParam(value = "cardSet",       required = false) String cardSet,
            @RequestParam(value = "grade",         required = false) String grade,
            @RequestParam(value = "gradeCompany",  required = false) String gradeCompany,
            @RequestParam(value = "notes",         required = false) String notes,
            @RequestParam(value = "estimatedValue",required = false) BigDecimal estimatedValue,
            // imageUrl fallback: if no file is uploaded, caller may pass a URL directly
            @RequestParam(value = "imageUrl",      required = false) String imageUrlFallback,
            @RequestParam(value = "file",          required = false) MultipartFile file
    ) throws IOException {

        String imageUrl = imageUrlFallback;

        if (file != null && !file.isEmpty()) {
            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String filename    = SupabaseStorageService.uniqueFilename(file.getOriginalFilename());
            imageUrl = storageService.upload(file.getBytes(), BUCKET_CARD_IMAGES, filename, contentType);
        }

        BinderCard card = BinderCard.builder()
                .user(user)
                .cardName(cardName)
                .cardType(cardType)
                .cardSet(cardSet)
                .imageUrl(imageUrl)
                .grade(grade)
                .gradeCompany(gradeCompany)
                .notes(notes)
                .estimatedValue(estimatedValue)
                .build();
        binderCardRepository.save(card);

        return ResponseEntity.status(HttpStatus.CREATED).body(CardResponse.from(card));
    }

    // ── DELETE /api/binder/{id} ───────────────────────────────────────────────
    @DeleteMapping("/{id}")
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

    // ── Inline DTO ────────────────────────────────────────────────────────────

    @Data
    public static class CardResponse {
        private Long       id;
        private String     cardName;
        private String     cardType;
        private String     cardSet;
        private String     imageUrl;
        private String     grade;
        private String     gradeCompany;
        private String     notes;
        private BigDecimal estimatedValue;
        private LocalDateTime addedAt;

        public static CardResponse from(BinderCard c) {
            CardResponse r = new CardResponse();
            r.id             = c.getId();
            r.cardName       = c.getCardName();
            r.cardType       = c.getCardType();
            r.cardSet        = c.getCardSet();
            r.imageUrl       = c.getImageUrl();
            r.grade          = c.getGrade();
            r.gradeCompany   = c.getGradeCompany();
            r.notes          = c.getNotes();
            r.estimatedValue = c.getEstimatedValue();
            r.addedAt        = c.getAddedAt();
            return r;
        }
    }
}
