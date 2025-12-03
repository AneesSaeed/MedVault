import { Injectable } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';

@Injectable({ // This decorator marks the class as something Angular’s dependency injection system can use. Without it, Angular cannot inject this class into components.
  providedIn: 'root' // create one instance of the service at application startup, store it in the root injector (global scope), reuse that same instance everywhere it is injected
})
export class AuthService {
  private readonly data: any;

  constructor(private keycloak: KeycloakService) {
    this.data = keycloak.getKeycloakInstance().tokenParsed || {};
    console.log(keycloak.getKeycloakInstance().token);

  }

  get username() {
    return this.data.preferred_username;
  }

  get firstName() {
    return this.data.given_name;
  }

  get lastName() {
    return this.data.family_name;
  }

  get email() {
    return this.data.email;
  }

  logout() {
    return this.keycloak.logout('http://localhost:4200');
  }
}
