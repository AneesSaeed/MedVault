import { Component } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent {

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
