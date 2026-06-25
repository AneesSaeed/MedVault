package be.he2b.healthsec.medical_records.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import be.he2b.healthsec.medical_records.logging.LoggingService;
import lombok.RequiredArgsConstructor;

/**
 * In-memory rate limiting service.
 *
 * <p>Protects the application against brute-force attacks and API abuse by
 * limiting the number of requests per user and per request type.</p>
 */
@Component
@RequiredArgsConstructor
public class RateLimitService {

    private final LoggingService logger;

    /** Per-minute limits per request type */
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final int MAX_FILE_UPLOADS_PER_MINUTE = 10;

    /**
     * In-memory counters indexed by user identifier.
     * userId -> RequestCounter
     */
    private final Map<String, RequestCounter> generalRequestCounters = new ConcurrentHashMap<>();
    private final Map<String, RequestCounter> fileUploadCounters = new ConcurrentHashMap<>();

    /**
     * Checks whether a request is allowed for a given user and request type.
     *
     * @param userId identifier of the authenticated user
     * @param requestType request category (GENERAL or FILE_UPLOAD)
     * @return {@code true} if the request is allowed, {@code false} if the limit is exceeded
     */
    public boolean isRequestAllowed(String userId, RequestType requestType) {
        if (userId == null) {
            // Defensive fail-closed: this service expects authenticated requests only.
            logger.logSecurityEvent("RATE_LIMIT_UNEXPECTED_ANONYMOUS", "anonymous", "HIGH", Map.of("requestType", requestType.name()));
            return false;
        }


        Map<String, RequestCounter> counters;
        int maxRequests;

        switch (requestType) {
            case FILE_UPLOAD:
                counters = fileUploadCounters;
                maxRequests = MAX_FILE_UPLOADS_PER_MINUTE;
                break;
            case GENERAL:
            default:
                counters = generalRequestCounters;
                maxRequests = MAX_REQUESTS_PER_MINUTE;
                break;
        }

        RequestCounter counter = counters.computeIfAbsent(userId, k -> new RequestCounter());
        int currentCount = counter.increment();

        if (currentCount > maxRequests) {
            logger.logSecurityEvent(
                "RATE_LIMIT_EXCEEDED",
                userId,
                "HIGH",
                Map.of(
                    "requestType", requestType.name(),
                    "currentCount", currentCount,
                    "maxAllowed", maxRequests
                )
            );
            return false;
        }

        return true;
    }

    /**
     * Records a blocked request after rate-limit evaluation.
     *
     * @param userId identifier of the user
     * @param requestType request category
     * @param allowed whether the request was allowed
     */
    public void recordRequest(String userId, RequestType requestType, boolean allowed) {
        if (!allowed) {
            logger.logAction(
                "REQUEST_BLOCKED_RATE_LIMIT",
                userId,
                Map.of("requestType", requestType.name())
            );
        }
    }

    /**
     * Resets all rate-limit counters every minute.
     *
     * <p>This scheduled task enforces a fixed per-minute window and clears
     * all in-memory counters.</p>
     */
    @Scheduled(fixedDelay = 60000)
    public void resetCounters() {
        int totalGeneral = generalRequestCounters.size();
        int totalUploads = fileUploadCounters.size();

        generalRequestCounters.clear();
        fileUploadCounters.clear();

        if (totalGeneral > 0 || totalUploads > 0) {
            logger.logAction(
                "RATE_LIMIT_COUNTERS_RESET",
                "system",
                Map.of(
                    "generalUsersTracked", totalGeneral,
                    "uploadUsersTracked", totalUploads
                )
            );
        }
    }

    /**
     * Supported request categories for rate limiting.
     */
    public enum RequestType {
        GENERAL,      // General API requests
        FILE_UPLOAD   // Medical file uploads
    }

    /**
     * Thread-safe per-user request counter.
     */
    private static class RequestCounter {
        private final AtomicInteger count = new AtomicInteger(0);

        public int increment() {
            return count.incrementAndGet();
        }

        public int get() {
            return count.get();
        }
    }

    /**
     * Returns the current request count for a user.
     *
     * <p>Intended for debugging and testing purposes only.</p>
     */
    public int getCurrentCount(String userId, RequestType requestType) {
        Map<String, RequestCounter> counters =
            requestType == RequestType.FILE_UPLOAD
                ? fileUploadCounters
                : generalRequestCounters;

        RequestCounter counter = counters.get(userId);
        return counter != null ? counter.get() : 0;
    }
}
