package be.he2b.healthsec.medical_records.logging;

import lombok.Getter;

import java.util.Map;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Centralized structured logging helper.
 *
 * <p>Used to emit consistent, parse-friendly logs (API requests, user actions, security events,
 * crypto operations). Integration/forwarding (e.g., Logstash/ELK) is handled by logback config.</p>
 */
@Service
public class LoggingService {
  
  private static final Logger LOGGER = LoggerFactory.getLogger(LoggingService.class);

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
  public void info(String message, Map<String, ?> metadata) {
    LOGGER.info("[{}] {}", "INFO", formatWithMetadata(message, metadata));
  }

  /**
   * Log info message
   */
  public void info(String message) {
    LOGGER.info("{}", message);
  }

  /**
   * Log debug message with metadata
   */
  public void debug(String message, Map<String, ?> metadata) {
    LOGGER.debug("[{}] {}", "DEBUG", formatWithMetadata(message, metadata));
  }

  /**
   * Log debug message
   */
  public void debug(String message) {
    LOGGER.debug("{}", message);
  }

  /**
   * Log warning message with metadata
   */
  public void warn(String message, Map<String, ?> metadata) {
    LOGGER.warn("[{}] {}", "WARN", formatWithMetadata(message, metadata));
  }

  /**
   * Log warning message
   */
  public void warn(String message) {
    LOGGER.warn("{}", message);
  }

  /**
   * Log error with exception and metadata
   */
  public void error(String message, Exception e, Map<String, ?> metadata) {
    LOGGER.error("[{}] {} | Metadata: {}", "ERROR", message, metadata, e);
  }

  /**
   * Log error with exception
   */
  public void error(String message, Exception e) {
    LOGGER.error("[{}] {}", "ERROR", message, e);
  }

  /**
   * Log error message
   */
  public void error(String message) {
    LOGGER.error("{}", message);
  }

  /**
   * Log critical/fatal error
   */
  public void fatal(String message, Exception e, Map<String, ?> metadata) {
    LOGGER.error("[FATAL] {} | Metadata: {}", message, metadata, e);
  }

  /**
   * Log security event (audit trail)
   */
  public void logSecurityEvent(
      String eventType,
      String userId,
      String severity,
      Map<String, ?> metadata
  ) {
    Map<String, Object> auditMetadata = new HashMap<>(metadata != null ? metadata : Map.of());
    auditMetadata.put("eventType", eventType);
    auditMetadata.put("userId", userId);
    auditMetadata.put("severity", severity);

    LOGGER.warn(
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
  public void logAction(String action, String userId, Map<String, ?> metadata) {
    Map<String, Object> actionMetadata = new HashMap<>(metadata != null ? metadata : Map.of());
    actionMetadata.put("action", action);
    actionMetadata.put("userId", userId);

    LOGGER.info("[ACTION] {} | User: {} | Metadata: {}", action, userId, actionMetadata);
  }

  /**
   * Log API request
   */
  public void logApiRequest(String method, String endpoint, String userId) {
    LOGGER.info("[API-REQUEST] {} {} | User: {}", method, endpoint, userId);
  }

  /**
   * Log API response with timing
   */
  public void logApiResponse(String method, String endpoint, int statusCode, long durationMs) {
    LOGGER.info("[API-RESPONSE] {} {} | Status: {} | Duration: {}ms", method, endpoint, statusCode, durationMs);
  }

  /**
   * Log encryption operation
   */
  public void logCrypto(String operation, boolean success, String userId, Map<String, ?> metadata) {
    Map<String, Object> cryptoMetadata = new HashMap<>(metadata != null ? metadata : Map.of());
    cryptoMetadata.put("operation", operation);
    cryptoMetadata.put("success", success);
    cryptoMetadata.put("userId", userId);

    LOGGER.info("[CRYPTO] {} | Success: {} | User: {} | Metadata: {}", operation, success, userId, cryptoMetadata);
  }

  /**
   * Format message with metadata for logging
   */
  private String formatWithMetadata(String message, Map<String, ?> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return message;
    }
    return message + " | " + metadata.toString();
  }
}
