import { Injectable, NgZone, inject } from '@angular/core';
import { Router } from '@angular/router';
import { KeycloakService } from 'keycloak-angular';
import { LoggingService } from './logging.service';

/**
 * Service de gestion du timeout d'inactivité
 * Déconnecte automatiquement l'utilisateur après 30 minutes d'inactivité
 * 
 * SECURITY: Implémentation de la recommandation du rapport de sécurité (Question 8)
 * Protège contre les attaques de data remanence en nettoyant la session inactive
 */
@Injectable({
  providedIn: 'root'
})
export class InactivityTimeoutService {
  private readonly INACTIVITY_TIMEOUT = 30 * 60 * 1000; // 30 minutes en millisecondes
  private readonly WARNING_BEFORE_LOGOUT = 2 * 60 * 1000; // 2 minutes avant déconnexion
  
  private timeoutId: number | null = null;
  private warningTimeoutId: number | null = null;
  private lastActivityTime: number = Date.now();
  
  private readonly keycloak = inject(KeycloakService);
  private readonly router = inject(Router);
  private readonly ngZone = inject(NgZone);
  private readonly logger = inject(LoggingService);

  /**
   * Démarre la surveillance de l'inactivité
   * À appeler après login réussi
   */
  startWatching(): void {
    this.logger.logAction('INACTIVITY_TIMEOUT_STARTED', 'system', {
      timeoutMinutes: this.INACTIVITY_TIMEOUT / 60000
    }, 'InactivityTimeoutService');

    // Écoute les événements d'activité utilisateur
    this.setupActivityListeners();
    
    // Démarre le timer
    this.resetTimer();
  }

  /**
   * Arrête la surveillance (lors du logout manuel)
   */
  stopWatching(): void {
    this.clearTimers();
    this.removeActivityListeners();
    
    this.logger.logAction('INACTIVITY_TIMEOUT_STOPPED', 'system', {}, 'InactivityTimeoutService');
  }

  /**
   * Réinitialise le timer d'inactivité
   * Appelé à chaque activité utilisateur
   */
  private resetTimer(): void {
    this.clearTimers();
    this.lastActivityTime = Date.now();

    // Timer principal (30 min)
    this.ngZone.runOutsideAngular(() => {
      this.timeoutId = window.setTimeout(() => {
        this.ngZone.run(() => this.handleInactivityLogout());
      }, this.INACTIVITY_TIMEOUT);

      // Timer d'avertissement (28 min = 30 - 2)
      this.warningTimeoutId = window.setTimeout(() => {
        this.ngZone.run(() => this.showWarning());
      }, this.INACTIVITY_TIMEOUT - this.WARNING_BEFORE_LOGOUT);
    });
  }

  /**
   * Affiche un avertissement avant déconnexion
   */
  private showWarning(): void {
    const timeRemaining = Math.ceil(this.WARNING_BEFORE_LOGOUT / 60000);
    
    this.logger.logAction('INACTIVITY_WARNING_SHOWN', 'system', {
      timeRemainingMinutes: timeRemaining
    }, 'InactivityTimeoutService');

    // Afficher une notification visuelle (peut être amélioré avec un modal)
    if (confirm(`Vous serez déconnecté dans ${timeRemaining} minutes par mesure de sécurité. Cliquez OK pour rester connecté.`)) {
      this.resetTimer(); // L'utilisateur a répondu, réinitialiser le timer
    }
  }

  /**
   * Déconnecte l'utilisateur après inactivité
   */
  private async handleInactivityLogout(): Promise<void> {
    const inactiveMinutes = Math.floor((Date.now() - this.lastActivityTime) / 60000);
    
    this.logger.logSecurityEvent(
      'INACTIVITY_LOGOUT',
      'system',
      'LOW',
      {
        inactiveMinutes,
        reason: 'Automatic logout after 30 minutes of inactivity'
      }
    );

    try {
      // Arrêter la surveillance
      this.stopWatching();
      
      // Déconnexion Keycloak
      await this.keycloak.logout(window.location.origin);
      
      // Redirection vers page d'accueil
      this.router.navigate(['/']);
    } catch (error) {
      this.logger.error('Error during inactivity logout', error);
    }
  }

  /**
   * Configure les listeners d'activité utilisateur
   */
  private setupActivityListeners(): void {
    // Liste des événements considérés comme "activité"
    const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];
    
    this.ngZone.runOutsideAngular(() => {
      events.forEach(event => {
        document.addEventListener(event, this.activityHandler, true);
      });
    });
  }

  /**
   * Supprime les listeners d'activité
   */
  private removeActivityListeners(): void {
    const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click'];
    
    events.forEach(event => {
      document.removeEventListener(event, this.activityHandler, true);
    });
  }

  /**
   * Handler appelé lors d'une activité utilisateur
   */
  private activityHandler = (): void => {
    // Throttle: ne réinitialise pas le timer plus d'une fois par minute
    const timeSinceLastActivity = Date.now() - this.lastActivityTime;
    if (timeSinceLastActivity > 60000) { // 1 minute
      this.ngZone.run(() => this.resetTimer());
    }
  };

  /**
   * Nettoie les timers
   */
  private clearTimers(): void {
    if (this.timeoutId !== null) {
      clearTimeout(this.timeoutId);
      this.timeoutId = null;
    }
    if (this.warningTimeoutId !== null) {
      clearTimeout(this.warningTimeoutId);
      this.warningTimeoutId = null;
    }
  }
}
