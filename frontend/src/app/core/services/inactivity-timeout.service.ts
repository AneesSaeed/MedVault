import { Injectable, NgZone, inject } from '@angular/core';
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
  private readonly WARNING_BEFORE_LOGOUT = 2 * 60 * 1000; //  2 minutes avant déconnexion
  
  private timeoutId: number | null = null;
  private warningTimeoutId: number | null = null;
  private countdownIntervalId: number | null = null;
  private lastActivityTime: number = Date.now();
  private isLoggingOut = false; // Protection contre les déconnexions multiples
  private modalElement: HTMLElement | null = null;
  
  private readonly keycloak = inject(KeycloakService);
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
    this.removeModal(); // Supprimer la modal si elle est affichée
    this.isLoggingOut = false; // Réinitialiser le flag
    
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
   * Affiche un avertissement avant déconnexion avec compteur qui descend
   * IMPORTANT: Le timer principal (timeoutId) continue à tourner même si la modal est affichée.
   * Si l'utilisateur ne répond pas, il sera déconnecté automatiquement quand le timer atteint le timeout.
   */
  private showWarning(): void {
    const timeRemainingSeconds = Math.ceil(this.WARNING_BEFORE_LOGOUT / 1000);
    
    this.logger.logAction('INACTIVITY_WARNING_SHOWN', 'system', {
      timeRemainingSeconds
    }, 'InactivityTimeoutService');

    // Créer et afficher la modal avec compteur
    this.createAndShowModal(timeRemainingSeconds);
  }

  /**
   * Crée et affiche une modal avec compteur qui descend
   */
  private createAndShowModal(initialSeconds: number): void {
    // Supprimer la modal existante si elle existe
    this.removeModal();

    let remainingSeconds = initialSeconds;

    // Créer le backdrop
    const backdrop = document.createElement('div');
    backdrop.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0, 0, 0, 0.5);
      backdrop-filter: blur(4px);
      z-index: 10000;
    `;

    // Créer la modal
    const modal = document.createElement('div');
    modal.style.cssText = `
      position: fixed;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 450px;
      padding: 30px;
      background: white;
      border-radius: 12px;
      box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
      z-index: 10001;
      text-align: center;
    `;

    // Compteur
    const countdownElement = document.createElement('div');
    countdownElement.id = 'inactivity-countdown';
    countdownElement.style.cssText = `
      font-size: 48px;
      font-weight: bold;
      color: #e74c3c;
      margin: 20px 0;
    `;
    countdownElement.textContent = `${remainingSeconds}`;

    // Message
    const message = document.createElement('p');
    message.style.cssText = `
      font-size: 16px;
      color: #333;
      margin: 20px 0;
      line-height: 1.5;
    `;
    message.textContent = 'Vous serez déconnecté automatiquement par mesure de sécurité.';

    // Instructions
    const instructions = document.createElement('p');
    instructions.style.cssText = `
      font-size: 14px;
      color: #666;
      margin: 10px 0 20px 0;
    `;
    instructions.innerHTML = '• Cliquez <strong>Rester connecté</strong> pour continuer<br>• Cliquez <strong>Déconnexion</strong> pour vous déconnecter maintenant';

    // Boutons
    const buttonContainer = document.createElement('div');
    buttonContainer.style.cssText = `
      display: flex;
      gap: 12px;
      justify-content: center;
      margin-top: 20px;
    `;

    const stayButton = document.createElement('button');
    stayButton.textContent = 'Rester connecté';
    stayButton.style.cssText = `
      padding: 12px 24px;
      background: #27ae60;
      color: white;
      border: none;
      border-radius: 6px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
      transition: background 0.2s;
    `;
    stayButton.onmouseover = () => stayButton.style.background = '#229954';
    stayButton.onmouseout = () => stayButton.style.background = '#27ae60';
    stayButton.onclick = () => {
      this.removeModal();
      this.resetTimer();
    };

    const logoutButton = document.createElement('button');
    logoutButton.textContent = 'Déconnexion';
    logoutButton.style.cssText = `
      padding: 12px 24px;
      background: #e74c3c;
      color: white;
      border: none;
      border-radius: 6px;
      font-size: 16px;
      font-weight: 600;
      cursor: pointer;
      transition: background 0.2s;
    `;
    logoutButton.onmouseover = () => logoutButton.style.background = '#c0392b';
    logoutButton.onmouseout = () => logoutButton.style.background = '#e74c3c';
    logoutButton.onclick = () => {
      this.removeModal();
      this.handleInactivityLogout();
    };

    buttonContainer.appendChild(stayButton);
    buttonContainer.appendChild(logoutButton);

    modal.appendChild(countdownElement);
    modal.appendChild(message);
    modal.appendChild(instructions);
    modal.appendChild(buttonContainer);

    backdrop.appendChild(modal);
    document.body.appendChild(backdrop);

    this.modalElement = backdrop;

    // Démarrer le compteur
    this.countdownIntervalId = window.setInterval(() => {
      remainingSeconds--;
      countdownElement.textContent = `${remainingSeconds}`;

      if (remainingSeconds <= 0) {
        // Le compteur arrive à 0, fermer la modal et déconnecter
        this.removeModal();
        this.handleInactivityLogout();
      }
    }, 1000);
  }

  /**
   * Supprime la modal si elle existe
   */
  private removeModal(): void {
    if (this.countdownIntervalId !== null) {
      clearInterval(this.countdownIntervalId);
      this.countdownIntervalId = null;
    }

    if (this.modalElement) {
      this.modalElement.remove();
      this.modalElement = null;
    }
  }

  /**
   * Déconnecte l'utilisateur après inactivité
   * Protection contre les appels multiples (peut être appelé par le timer principal ou par l'utilisateur qui clique Annuler)
   */
  private async handleInactivityLogout(): Promise<void> {
    // Protection contre les déconnexions multiples
    if (this.isLoggingOut) {
      return;
    }
    this.isLoggingOut = true;

    const inactiveMinutes = Math.floor((Date.now() - this.lastActivityTime) / 60000);
    
    this.logger.logSecurityEvent(
      'INACTIVITY_LOGOUT',
      'system',
      'LOW',
      {
        inactiveMinutes,
        reason: 'Automatic logout after inactivity timeout'
      }
    );

    try {
      // Arrêter la surveillance
      this.stopWatching();
      
      // Déconnexion Keycloak avec redirection directe vers la page de login
      // Utiliser l'URL de logout avec id_token_hint pour éviter la page de confirmation Keycloak
      const keycloakInstance = this.keycloak.getKeycloakInstance();
      const idToken = keycloakInstance.idToken;
      
      if (idToken) {
        // Utiliser l'URL de logout avec id_token_hint pour déconnexion automatique sans confirmation
        const redirectUri = encodeURIComponent('https://localhost/');
        window.location.href = `https://localhost/auth/realms/health-realm/protocol/openid-connect/logout?id_token_hint=${idToken}&post_logout_redirect_uri=${redirectUri}`;
      } else {
        // Pas de token, rediriger directement vers la page de login
        window.location.href = 'https://localhost/auth/realms/health-realm/protocol/openid-connect/auth?client_id=public-client&redirect_uri=https://localhost/&response_type=code&scope=openid';
      }
    } catch (error) {
      this.logger.error('Error during inactivity logout', error);
      // En cas d'erreur, rediriger directement vers la page de login Keycloak
      window.location.href = 'https://localhost/auth/realms/health-realm/protocol/openid-connect/auth?client_id=public-client&redirect_uri=https://localhost/&response_type=code&scope=openid';
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
    // Throttle: ne réinitialise pas le timer plus d'une fois toutes les 10 secondes (TEMPORAIRE pour test)
    const timeSinceLastActivity = Date.now() - this.lastActivityTime;
    if (timeSinceLastActivity > 10000) { // 10 secondes (TEMPORAIRE pour test)
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
    if (this.countdownIntervalId !== null) {
      clearInterval(this.countdownIntervalId);
      this.countdownIntervalId = null;
    }
  }
}
