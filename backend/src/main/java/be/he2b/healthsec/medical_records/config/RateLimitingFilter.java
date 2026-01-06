package be.he2b.healthsec.medical_records.config;

import java.io.IOException;
import java.util.Map;

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
 * Per-request rate limiting filter.
 *
 * <p>Enforces a per-minute rate limit using the authenticated username as the key,
 * or a shared anonymous bucket ({@code null}) for unauthenticated requests.</p>
 *
 * <p>On limit exceeded:
 * <ul>
 *   <li>Logs a security event with request metadata (path, method)</li>
 *   <li>Returns HTTP 429 with a JSON error payload</li>
 * </ul>
 * </p>
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int TOO_MANY_REQUESTS = 429;
    private static final String JSON = "application/json";
    private static final String RATE_LIMIT_EVENT = "REQUEST_BLOCKED_RATE_LIMIT";
    private static final String SEVERITY_HIGH = "HIGH";
    private static final String ERROR_BODY = "{\"error\":\"Too many requests. Please try again later.\"}";

    private final RateLimitService rateLimitService;
    private final LoggingService loggingService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String userId = resolveUserId();

        boolean allowed = rateLimitService.isRequestAllowed(userId, RateLimitService.RequestType.GENERAL);
        rateLimitService.recordRequest(userId, RateLimitService.RequestType.GENERAL, allowed);

        if (!allowed) {
            logRateLimitBlocked(request, userId);
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Resolves the identifier used for per-user rate limiting.
     *
     * @return authenticated principal name when available; otherwise {@code null}.
     */
    private String resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }

    private void logRateLimitBlocked(HttpServletRequest request, String userId) {
        loggingService.logSecurityEvent(
                RATE_LIMIT_EVENT,
                userId == null ? "anonymous" : userId,
                SEVERITY_HIGH,
                Map.of(
                        "path", request.getRequestURI(),
                        "method", request.getMethod()
                )
        );
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(TOO_MANY_REQUESTS);
        response.setContentType(JSON);
        response.getWriter().write(ERROR_BODY);
    }
}
