package be.he2b.healthsec.medical_records.config;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import be.he2b.healthsec.medical_records.logging.LoggingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Simple rate limiting filter that blocks requests exceeding per-minute caps.
 * Applies to authenticated users based on their principal name; falls back to remote address.
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final LoggingService loggingService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userId = resolveUserId();

        boolean allowed = rateLimitService.isRequestAllowed(userId, RateLimitService.RequestType.GENERAL);
        rateLimitService.recordRequest(userId, RateLimitService.RequestType.GENERAL, allowed);

        if (!allowed) {
            loggingService.logSecurityEvent(
                "REQUEST_BLOCKED_RATE_LIMIT",
                userId == null ? "anonymous" : userId,
                "HIGH",
                java.util.Map.of(
                    "path", request.getRequestURI(),
                    "method", request.getMethod()
                )
            );
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }
}
