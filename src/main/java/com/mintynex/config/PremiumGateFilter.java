package com.mintynex.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mintynex.users.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * FIX #29 — Premium gate bypass via sessionStorage clear.
 *
 * Previously the frontend stored premium status in sessionStorage, which is
 * wiped on hard refresh, letting users bypass the gate.
 *
 * This filter runs server-side BEFORE the controller and blocks non-premium
 * users from premium API routes regardless of what the frontend thinks.
 *
 * Premium routes (require plan != FREE):
 *   /api/binder/**
 *   /api/trades/**
 *   /api/listings/**
 *   /api/messages/**
 *
 * Public / auth routes are excluded from this check entirely.
 */
@Component
public class PremiumGateFilter extends OncePerRequestFilter {

    private static final List<String> PREMIUM_PREFIXES = List.of(
            "/api/binder",
            "/api/trades",
            "/api/listings",
            "/api/messages"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        boolean isPremiumRoute = PREMIUM_PREFIXES.stream().anyMatch(path::startsWith);

        if (isPremiumRoute) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
                if (!isPremiumActive(user)) {
                    response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                            "error", "PREMIUM_REQUIRED",
                            "message", "This feature requires a MintyNex Premium plan."
                    )));
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * User is premium-active if:
     *   - premium flag is true AND
     *   - plan is not FREE AND
     *   - premiumExpiresAt is null (lifetime) OR is in the future
     */
    private boolean isPremiumActive(User user) {
        if (!user.isPremium()) return false;
        if (user.getPremiumPlan() == User.PremiumPlan.FREE) return false;
        if (user.getPremiumExpiresAt() == null) return true;         // lifetime / admin-granted
        return user.getPremiumExpiresAt().isAfter(LocalDateTime.now());
    }
}