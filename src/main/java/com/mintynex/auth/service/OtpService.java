package com.mintynex.auth.service;

import com.mintynex.auth.model.OtpCode;
import com.mintynex.auth.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpCodeRepository otpCodeRepository;

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public void sendOtp(String phone, OtpCode.Purpose purpose) {
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
        // OPTION A — Twilio (recommended, see instructions below):
        //   1. Add dependency: com.twilio.sdk:twilio:9.14.0
        //   2. Add to application.properties:
        //        twilio.account-sid=ACxxxx
        //        twilio.auth-token=xxxx
        //        twilio.from-number=+1XXXXXXXXXX
        //   3. Replace the log line below with:
        //        Twilio.init(accountSid, authToken);
        //        Message.creator(new PhoneNumber(phone),
        //                        new PhoneNumber(fromNumber),
        //                        "Your MintyNex OTP: " + code +
        //                        " (expires in 10 minutes)").create();
        //
        // OPTION B — Supabase Edge Function (free, no extra library):
        //   Create a Supabase Edge Function that wraps an SMS provider
        //   and POST to it from here via RestTemplate.
        // ─────────────────────────────────────────────────────────────────────

        log.info("[OTP] Phone: {} | Purpose: {} | Code: {} | Expires: {}",
                phone, purpose, code,
                LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));

        // In production, remove the log above (exposes OTP in logs)
        // and send via Twilio/SMS provider.
        deliverSms(phone, "Your MintyNex OTP: " + code + " (valid 10 min). Do not share.");
    }

    private void deliverSms(String phone, String message) {
        // TODO: replace with real SMS provider call
        log.debug("[SMS → {}] {}", phone, message);
    }
}
