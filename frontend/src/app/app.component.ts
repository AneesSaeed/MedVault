/**
 * This is the root UI component of the Angular application.
 *
 * It:
 * - Shows the logged-in user's username
 * - Provides a logout button
 */

import { Component } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
})
export class AppComponent {

  constructor(public keycloak: KeycloakService) {}

  /**
   * Extracts the username from the Keycloak token.
   *
   * tokenParsed is a dynamic object, so we access the field using ['preferred_username'].
   */
  get username() {
    return this.keycloak.getKeycloakInstance().tokenParsed?.['preferred_username']
  }

  logout() {
    this.keycloak.logout('http://localhost:4200');
  }
}
