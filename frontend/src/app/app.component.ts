/**
 * This is the root UI component of the Angular application.
 */

import { Component, OnInit, inject } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';
import { InactivityTimeoutService } from './core/services/inactivity-timeout.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  private readonly keycloak = inject(KeycloakService);
  private readonly inactivityTimeout = inject(InactivityTimeoutService);

  async ngOnInit(): Promise<void> {
    // Démarre la surveillance d'inactivité si l'utilisateur est authentifié
    const isLoggedIn = await this.keycloak.isLoggedIn();
    if (isLoggedIn) {
      this.inactivityTimeout.startWatching();
    }
  }
}
