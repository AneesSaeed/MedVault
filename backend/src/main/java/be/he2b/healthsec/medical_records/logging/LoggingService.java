package be.he2b.healthsec.medical_records.logging;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Centralized logging service for Spring Boot backend
 *
 * This service integrates with Logstash/ELK Stack via logback configuration.
 * All logs are sent to Logstash (TCP port 5000) for centralized monitoring.
 *
 * Usage in service classes:
 *   private static final Logger logger = LoggerFactory.getLogger(MyService.class);
 *
 * Or use this service for structured logging:
 *   @Autowired
 *   private LoggingService loggingService;
 *
 *   loggingService.info("User login", Map.of(
 *     "userId", keycloakId,
 *     "username", username
 *   ));
 */
@Service
public class LoggingService {
  private static final Logger logger = LoggerFactory.getLogger(LoggingService.class);

  @Getter
  public enum LogLevel {
    TRACE("TRACE"),
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
    FATAL("FATAL");

    private final String value;

    LogLevel(String value) {
      this.value = value;
    }
  }

  /**
   * Log general info message with metadata
   */
  public void info(String message, java.util.Map<String, ?> metadata) {
    logger.info("[{}] {}", "INFO", formatWithMetadata(message, metadata));
  }

  /**
   * Log info message
   */
  public void info(String message) {
    logger.info("{}", message);
  }

  /**
   * Log debug message with metadata
   */
  public void debug(String message, java.util.Map<String, ?> metadata) {
    logger.debug("[{}] {}", "DEBUG", formatWithMetadata(message, metadata));
  }

  /**
   * Log debug message
   */
  public void debug(String message) {
    logger.debug("{}", message);
  }

  /**
   * Log warning message with metadata
   */
  public void warn(String message, java.util.Map<String, ?> metadata) {
    logger.warn("[{}] {}", "WARN", formatWithMetadata(message, metadata));
  }

  /**
   * Log warning message
   */
  public void warn(String message) {
    logger.warn("{}", message);
  }

  /**
   * Log error with exception and metadata
   */
  public void error(String message, Exception e, java.util.Map<String, ?> metadata) {
    logger.error("[{}] {} | Metadata: {}", "ERROR", message, metadata, e);
  }

  /**
   * Log error with exception
   */
  public void error(String message, Exception e) {
    logger.error("[{}] {}", "ERROR", message, e);
  }

  /**
   * Log error message
   */
  public void error(String message) {
    logger.error("{}", message);
  }

  /**
   * Log critical/fatal error
   */
  public void fatal(String message, Exception e, java.util.Map<String, ?> metadata) {
    logger.error("[FATAL] {} | Metadata: {}", message, metadata, e);
  }

  /**
   * Log security event (audit trail)
   */
  public void logSecurityEvent(
      String eventType,
      String userId,
      String severity,
      java.util.Map<String, ?> metadata
  ) {
    java.util.Map<String, Object> auditMetadata = new java.util.HashMap<>(metadata != null ? metadata : java.util.Map.of());
    auditMetadata.put("eventType", eventType);
    auditMetadata.put("userId", userId);
    auditMetadata.put("severity", severity);

    logger.warn(
        "[SECURITY] {} | User: {} | Severity: {} | Metadata: {}",
        eventType,
        userId,
        severity,
        auditMetadata
    );
  }

  /**
   * Log user action (for audit trail)
   */
  public void logAction(String action, String userId, java.util.Map<String, ?> metadata) {
    java.util.Map<String, Object> actionMetadata = new java.util.HashMap<>(metadata != null ? metadata : java.util.Map.of());
    actionMetadata.put("action", action);
    actionMetadata.put("userId", userId);

    logger.info("[ACTION] {} | User: {} | Metadata: {}", action, userId, actionMetadata);
  }

  /**
   * Log API request
   */
  public void logApiRequest(String method, String endpoint, String userId) {
    logger.info("[API-REQUEST] {} {} | User: {}", method, endpoint, userId);
  }

  /**
   * Log API response with timing
   */
  public void logApiResponse(String method, String endpoint, int statusCode, long durationMs) {
    logger.info("[API-RESPONSE] {} {} | Status: {} | Duration: {}ms", method, endpoint, statusCode, durationMs);
  }

  /**
   * Log encryption operation
   */
  public void logCrypto(String operation, boolean success, String userId, java.util.Map<String, ?> metadata) {
    java.util.Map<String, Object> cryptoMetadata = new java.util.HashMap<>(metadata != null ? metadata : java.util.Map.of());
    cryptoMetadata.put("operation", operation);
    cryptoMetadata.put("success", success);
    cryptoMetadata.put("userId", userId);

    logger.info("[CRYPTO] {} | Success: {} | User: {} | Metadata: {}", operation, success, userId, cryptoMetadata);
  }

  /**
   * Format message with metadata for logging
   */
  private String formatWithMetadata(String message, java.util.Map<String, ?> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return message;
    }
    return message + " | " + metadata.toString();
  }
}
