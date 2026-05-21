package com.mintynex.auth.service;

import com.mintynex.auth.model.OtpCode;
import com.mintynex.auth.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * MODIFIED — src/main/java/com/mintynex/auth/service/OtpService.java
 *
 * Change: sendOtp() now returns the generated code as a String so that
 * AuthService can embed it in the API response message as [DEV CODE: xxxxxx].
 * The log statements are kept for server-side debugging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpCodeRepository otpCodeRepository;

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates, persists, and "sends" (currently logs) an OTP.
     *
     * @return the generated 6-digit code string — caller embeds it in DEV response
     */
    @Transactional
    public String sendOtp(String phone, OtpCode.Purpose purpose) {
        // Invalidate all previous OTPs for this phone+purpose
        otpCodeRepository.invalidateAll(phone, purpose);

        // Generate 6-digit code
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        // Persist
        OtpCode otpCode = OtpCode.builder()
                .phone(phone)
                .code(code)
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .build();
        otpCodeRepository.save(otpCode);

        // ── SMS Delivery ──────────────────────────────────────────────────────
        // Currently logs to console for dev. To go live, pick ONE option:
        //
        // OPTION A — Twilio (recommended):
        //   1. Add dependency: com.twilio.sdk:twilio:9.14.0
        //   2. Add to application.properties:
        //        twilio.account-sid=ACxxxx
        //        twilio.auth-token=xxxx
        //        twilio.from-number=+1XXXXXXXXXX
        //   3. Replace deliverSms() below with real Twilio call.
        //
        // OPTION B — Supabase Edge Function:
        //   POST to your edge function URL with { phone, message } payload.
        // ─────────────────────────────────────────────────────────────────────

        log.info("[OTP] Phone: {} | Purpose: {} | Code: {} | Expires: {}",
                phone, purpose, code,
                LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));

        deliverSms(phone, "Your MintyNex OTP: " + code + " (valid 10 min). Do not share.");

        return code; // returned so AuthService can include [DEV CODE: xxxxxx] in response
    }

    private void deliverSms(String phone, String message) {
        // TODO: replace with real SMS provider call
        log.debug("[SMS → {}] {}", phone, message);
    }
}
