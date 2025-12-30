package be.he2b.healthsec.medical_records.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import be.he2b.healthsec.medical_records.logging.LoggingService;
import lombok.RequiredArgsConstructor;

/**
 * Service de rate limiting pour protéger contre les attaques par force brute
 * et les abus d'API
 * 
 * SECURITY: Implémentation de la recommandation du rapport de sécurité (Question 10)
 * Limite le nombre de requêtes par utilisateur pour détecter les comportements malveillants
 */
@Component
@RequiredArgsConstructor
public class RateLimitService {

    private final LoggingService logger;

    /**
     * Limites par type d'opération
     */
    private static final int MAX_REQUESTS_PER_MINUTE = 60; // Requêtes générales
    private static final int MAX_FILE_UPLOADS_PER_MINUTE = 10; // Uploads de fichiers
    
    /**
     * Stockage en mémoire des compteurs par utilisateur
     * Format: userId -> RequestCounter
     */
    private final Map<String, RequestCounter> generalRequestCounters = new ConcurrentHashMap<>();
    private final Map<String, RequestCounter> fileUploadCounters = new ConcurrentHashMap<>();

    /**
     * Vérifie si une requête est autorisée pour un utilisateur
     * 
     * @param userId ID de l'utilisateur (keycloakId)
     * @param requestType Type de requête (GENERAL, FILE_UPLOAD)
     * @return true si la requête est autorisée, false si rate limit dépassé
     */
    public boolean isRequestAllowed(String userId, RequestType requestType) {
        if (userId == null) {
            return true; // Pas de limitation pour les requêtes non authentifiées
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
     * Enregistre une tentative de requête après rate limiting
     * 
     * @param userId ID de l'utilisateur
     * @param requestType Type de requête
     * @param allowed Si la requête a été autorisée
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
     * Réinitialise les compteurs toutes les minutes
     * Scheduled task exécuté automatiquement
     */
    @Scheduled(fixedDelay = 60000) // 60 secondes
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
     * Types de requêtes pour le rate limiting
     */
    public enum RequestType {
        GENERAL,      // Requêtes API générales
        FILE_UPLOAD   // Uploads de fichiers médicaux
    }

    /**
     * Compteur thread-safe pour les requêtes
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
     * Obtient le nombre actuel de requêtes pour un utilisateur
     * (Utile pour le debugging et les tests)
     */
    public int getCurrentCount(String userId, RequestType requestType) {
        Map<String, RequestCounter> counters = requestType == RequestType.FILE_UPLOAD 
            ? fileUploadCounters 
            : generalRequestCounters;
        
        RequestCounter counter = counters.get(userId);
        return counter != null ? counter.get() : 0;
    }
}
