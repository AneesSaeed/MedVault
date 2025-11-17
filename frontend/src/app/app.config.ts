// Dans Angular, toutes les choses importantes—comme le routeur, les services et HttpClient—sont configurées dans un seul fichier principal.
// Dans Vue, c’est un peu pareil dans le fichier où tu appelles createApp(), généralement main.ts. C’est là que tu ajoutes le routeur, les plugins et tout ce dont l’application entière a besoin.

import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http'; // ca

import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(), // et ca
  ]
};
