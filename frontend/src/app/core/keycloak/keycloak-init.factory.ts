/**
 * This file initializes Keycloak BEFORE Angular starts.
 *
 * Why this is important:
 * - The user must be logged in before the Angular app loads.
 * - Keycloak redirects to the login page (with WebAuthn).
 * - After login, Angular continues loading normally.
 */

import { KeycloakService } from 'keycloak-angular';

export function initializeKeycloak(keycloak: KeycloakService) {
  // Angular will call this function before starting the app.
  return () =>
    keycloak.init({
      config: {
        url: 'https://localhost/auth',   // URL of your Keycloak server
        realm: 'health-realm',
        clientId: 'public-client'       // The Keycloak client ID configured for Angular
      },
      initOptions: {
        onLoad: 'login-required',         // Forces Keycloak login when the app loads
        checkLoginIframe: false,
        pkceMethod: 'S256'
      },
      bearerExcludedUrls: [] // REQUIRED after adding nginx HTTPS
    });
}
