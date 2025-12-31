import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/**
 * Log levels
 */
export enum LogLevel {
  TRACE = 'TRACE',
  DEBUG = 'DEBUG',
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
  FATAL = 'FATAL',
}

/**
 * Log entry structure sent to Logstash
 */
export interface LogEntry {
  timestamp: string;
  level: LogLevel;
  message: string;
  component?: string;
  userId?: string;
  action?: string;
  metadata?: Record<string, unknown>;
  stackTrace?: string;
}

@Injectable({
  providedIn: 'root'
})
export class LoggingService {
  // Send logs via nginx proxy to Logstash over TLS (accessed from browser)
  // Browser uses https://localhost/logs which gets proxied to logstash:5002 by nginx
  private readonly logstashUrl = 'https://localhost/logs';
  private readonly logLevel = environment.production ? LogLevel.INFO : LogLevel.DEBUG;
  private readonly http = inject(HttpClient);

  /**
   * Determine if a log should be sent based on level
   */
  private shouldLog(level: LogLevel): boolean {
    const levels = [LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL];
    const currentLevelIndex = levels.indexOf(this.logLevel);
    const messageLevelIndex = levels.indexOf(level);
    return messageLevelIndex >= currentLevelIndex;
  }

  /**
   * Send log to Logstash
   */
  private sendLog(entry: LogEntry): void {
    if (!this.shouldLog(entry.level)) return;

    // In production, use HttpClient; in dev, also log to console for debugging
    // Logstash HTTP input responds with "ok" (text), so we use responseType: 'text' to avoid JSON parsing errors
    const headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    // Use explicit typing to tell TypeScript we want text response, not JSON
    this.http.post<string>(this.logstashUrl, entry, { 
      headers,
      responseType: 'text' as 'json' // Type assertion needed because Angular's types are strict
    }).subscribe({
      next: () => {
        // Success - Logstash received the log (response is "ok" text, which is fine)
      },
      error: (err) => {
        // Logstash down? Fallback to console (don't recurse!)
        if (!environment.production) {
          console.warn('[LoggingService] Logstash unavailable:', err);
        }
      }
    });

    // Always log to console in development
    if (!environment.production) {
      this.logToConsole(entry);
    }
  }

  /**
   * Log to browser console with styling
   */
  private logToConsole(entry: LogEntry): void {
    const styles = this.getLogStyles(entry.level);
    const prefix = `[${entry.level}] ${entry.component || 'App'}`;

    console.log(
      `%c${prefix}`,
      styles,
      entry.message,
      entry.metadata || ''
    );

    if (entry.stackTrace) {
      console.error(entry.stackTrace);
    }
  }

  /**
   * Get console styles based on log level
   */
  private getLogStyles(level: LogLevel): string {
    const baseStyle = 'padding: 2px 4px; border-radius: 2px; font-weight: bold;';
    const colors: Record<LogLevel, string> = {
      [LogLevel.TRACE]: `${baseStyle} color: gray; background: #f0f0f0;`,
      [LogLevel.DEBUG]: `${baseStyle} color: blue; background: #e3f2fd;`,
      [LogLevel.INFO]: `${baseStyle} color: green; background: #e8f5e9;`,
      [LogLevel.WARN]: `${baseStyle} color: orange; background: #fff3e0;`,
      [LogLevel.ERROR]: `${baseStyle} color: white; background: #f44336;`,
      [LogLevel.FATAL]: `${baseStyle} color: white; background: #b71c1c;`,
    };
    return colors[level];
  }

  /**
   * Log trace message
   */
  trace(message: string, metadata?: Record<string, unknown>, component?: string): void {
    this.sendLog({
      timestamp: new Date().toISOString(),
      level: LogLevel.TRACE,
      message,
      metadata,
      component,
    });
  }

  /**
   * Log debug message
   */
  debug(message: string, metadata?: Record<string, unknown>, component?: string): void {
    this.sendLog({
      timestamp: new Date().toISOString(),
      level: LogLevel.DEBUG,
      message,
      metadata,
      component,
    });
  }

  /**
   * Log info message
   */
  info(message: string, metadata?: Record<string, unknown>, component?: string): void {
    this.sendLog({
      timestamp: new Date().toISOString(),
      level: LogLevel.INFO,
      message,
      metadata,
      component,
    });
  }

  /**
   * Log warning message
   */
  warn(message: string, metadata?: Record<string, unknown>, component?: string): void {
    this.sendLog({
      timestamp: new Date().toISOString(),
      level: LogLevel.WARN,
      message,
      metadata,
      component,
    });
  }

  /**
   * Log error message
   */
  error(message: string, error?: Error | unknown, metadata?: Record<string, unknown>, component?: string): void {
    let stackTrace: string | undefined;
    if (error instanceof Error) {
      stackTrace = error.stack;
    }

    this.sendLog({
      timestamp: new Date().toISOString(),
      level: LogLevel.ERROR,
      message,
      metadata: {
        ...metadata,
        errorType: error instanceof Error ? error.constructor.name : typeof error,
      },
      stackTrace,
      component,
    });
  }

  /**
   * Log critical/fatal error
   */
  fatal(message: string, error?: Error | unknown, metadata?: Record<string, unknown>, component?: string): void {
    let stackTrace: string | undefined;
    if (error instanceof Error) {
      stackTrace = error.stack;
    }

    this.sendLog({
      timestamp: new Date().toISOString(),
      level: LogLevel.FATAL,
      message,
      metadata: {
        ...metadata,
        errorType: error instanceof Error ? error.constructor.name : typeof error,
      },
      stackTrace,
      component,
    });
  }

  /**
   * Log user action (for audit trail)
   */
  logAction(
    action: string,
    userId: string,
    metadata?: Record<string, unknown>,
    component?: string
  ): void {
    this.sendLog({
      timestamp: new Date().toISOString(),
      level: LogLevel.INFO,
      message: `Action: ${action}`,
      userId,
      action,
      metadata,
      component,
    });
  }

  /**
   * Log security event
   */
  logSecurityEvent(
    eventType: string,
    userId: string,
    severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL',
    metadata?: Record<string, unknown>
  ): void {
    const level = severity === 'CRITICAL' ? LogLevel.FATAL : LogLevel.WARN;

    this.sendLog({
      timestamp: new Date().toISOString(),
      level,
      message: `Security Event: ${eventType}`,
      userId,
      metadata: {
        ...metadata,
        eventType,
        severity,
      },
      component: 'SecurityAudit',
    });
  }
}
