import { Injectable, NgZone, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { LoggingService } from './logging.service';
import { AuthService } from './auth.service';

export type WarningState = { remainingSeconds: number };

@Injectable({ providedIn: 'root' })
export class InactivityTimeoutService {
  private readonly INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000;
  private readonly WARNING_BEFORE_LOGOUT_MS = 60 * 1000;

  private timeoutId: number | null = null;
  private warningTimeoutId: number | null = null;
  private countdownIntervalId: number | null = null;

  private lastResetTime = Date.now();
  private isLoggingOut = false;
  private watching = false;

  private readonly warningSubject = new BehaviorSubject<WarningState | null>(null);
  readonly warning$ = this.warningSubject.asObservable();

  private readonly ngZone = inject(NgZone);
  private readonly logger = inject(LoggingService);
  private readonly auth = inject(AuthService);

  startWatching(): void {
    if (this.watching) return;
    this.watching = true;

    this.isLoggingOut = false;

    this.logger.logAction(
      'INACTIVITY_TIMEOUT_STARTED',
      'system',
      { timeoutMinutes: this.INACTIVITY_TIMEOUT_MS / 60000 },
      'InactivityTimeoutService'
    );

    this.setupActivityListeners();
    this.resetTimer();
  }

  stopWatching(): void {
    if (!this.watching) return;
    this.watching = false;

    this.clearTimers();
    this.removeActivityListeners();
    this.warningSubject.next(null);

    this.logger.logAction('INACTIVITY_TIMEOUT_STOPPED', 'system', {}, 'InactivityTimeoutService');
  }

  stayConnected(): void {
    if (!this.watching || this.isLoggingOut) return;
    this.warningSubject.next(null);
    this.resetTimer();
  }

  logoutNow(): void {
    if (!this.watching) return;
    this.warningSubject.next(null);
    void this.handleInactivityLogout();
  }

  private resetTimer(): void {
    if (!this.watching || this.isLoggingOut) return;

    this.clearTimers();
    this.warningSubject.next(null);
    this.lastResetTime = Date.now();

    this.ngZone.runOutsideAngular(() => {
      this.timeoutId = window.setTimeout(() => {
        this.ngZone.run(() => void this.handleInactivityLogout());
      }, this.INACTIVITY_TIMEOUT_MS);

      const warningDelay = this.INACTIVITY_TIMEOUT_MS - this.WARNING_BEFORE_LOGOUT_MS;
      this.warningTimeoutId = window.setTimeout(() => {
        this.ngZone.run(() => this.showWarning());
      }, Math.max(0, warningDelay));
    });
  }

  private showWarning(): void {
    if (!this.watching || this.isLoggingOut) return;
    if (this.warningSubject.value) return;

    const initialSeconds = Math.ceil(this.WARNING_BEFORE_LOGOUT_MS / 1000);

    this.logger.logAction(
      'INACTIVITY_WARNING_SHOWN',
      'system',
      { timeRemainingSeconds: initialSeconds },
      'InactivityTimeoutService'
    );

    this.warningSubject.next({ remainingSeconds: initialSeconds });

    // Countdown runs; do NOT treat incidental user clicks as activity anymore.
    this.countdownIntervalId = window.setInterval(() => {
      const current = this.warningSubject.value;
      if (!current) return;

      const next = current.remainingSeconds - 1;

      if (next <= 0) {
        this.warningSubject.next(null);
        void this.handleInactivityLogout();
      } else {
        this.warningSubject.next({ remainingSeconds: next });
      }
    }, 1000);
  }

  private async handleInactivityLogout(): Promise<void> {
    if (this.isLoggingOut) return;
    this.isLoggingOut = true;

    const inactiveMinutes = Math.floor((Date.now() - this.lastResetTime) / 60000);

    this.logger.logSecurityEvent('INACTIVITY_LOGOUT', 'system', 'LOW', {
      inactiveMinutes,
      reason: 'Automatic logout after inactivity timeout'
    });

    // Stop listeners/timers BEFORE redirect
    this.stopWatching();

    await this.auth.logout();
  }

  private setupActivityListeners(): void {
    // Keep it “intentful”. No mousemove.
    const events = ['keypress', 'click'];

    this.ngZone.runOutsideAngular(() => {
      for (const evt of events) {
        // capture=true means it sees events very early
        document.addEventListener(evt, this.activityHandler, true);
      }
    });
  }

  private removeActivityListeners(): void {
    const events = ['keypress', 'click'];
    for (const evt of events) {
      document.removeEventListener(evt, this.activityHandler, true);
    }
  }

  private activityHandler = (): void => {
    if (!this.watching || this.isLoggingOut) return;
    if (this.warningSubject.value) return;

    const timeSinceReset = Date.now() - this.lastResetTime;
    if (timeSinceReset > 10_000) {
      this.ngZone.run(() => this.resetTimer());
    }
  };

  private clearTimers(): void {
    if (this.timeoutId !== null) {
      clearTimeout(this.timeoutId);
      this.timeoutId = null;
    }
    if (this.warningTimeoutId !== null) {
      clearTimeout(this.warningTimeoutId);
      this.warningTimeoutId = null;
    }
    if (this.countdownIntervalId !== null) {
      clearInterval(this.countdownIntervalId);
      this.countdownIntervalId = null;
    }
  }
}
