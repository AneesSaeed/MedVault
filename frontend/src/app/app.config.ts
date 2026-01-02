import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import {
  provideKeycloak,
  includeBearerTokenInterceptor,
  createInterceptorCondition,
  IncludeBearerTokenCondition,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG
} from 'keycloak-angular';

import { routes } from './app-routing.module';

// If the request URL looks like https://localhost/api/... then automatically add
// Authorization: Bearer <JWT>
const apiCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: /^https:\/\/localhost\/api(\/.*)?$/i,
  bearerPrefix: 'Bearer'
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),

    provideKeycloak({
      config: {
        url: 'https://localhost/auth',
        realm: 'health-realm',
        clientId: 'public-client'
      },
      initOptions: {
        onLoad: 'login-required',
        checkLoginIframe: false,
        pkceMethod: 'S256',
        redirectUri: 'https://localhost/'
      }
    }),

    { provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG, useValue: [apiCondition] },
    provideHttpClient(withInterceptors([includeBearerTokenInterceptor]))
  ]
};
