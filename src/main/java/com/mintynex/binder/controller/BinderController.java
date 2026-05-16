package com.mintynex.binder.controller;

import com.mintynex.binder.model.BinderCard;
import com.mintynex.binder.repository.BinderCardRepository;
import com.mintynex.exception.NotFoundException;
import com.mintynex.users.model.User;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/binder")
@RequiredArgsConstructor
public class BinderController {

    private final BinderCardRepository binderCardRepository;

    // GET /api/binder?page=0&size=18
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

    // POST /api/binder
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

    // DELETE /api/binder/{id}
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

    // ── Inline DTOs ───────────────────────────────────────

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
            r.id = c.getId();
            r.cardName = c.getCardName();
            r.cardType = c.getCardType();
            r.cardSet = c.getCardSet();
            r.imageUrl = c.getImageUrl();
            r.grade = c.getGrade();
            r.gradeCompany = c.getGradeCompany();
            r.notes = c.getNotes();
            r.addedAt = c.getAddedAt();
            return r;
        }
    }
}
