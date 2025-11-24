/**
 * Root Angular module.
 *
 * This file:
 * - Imports KeycloakAngularModule
 * - Registers the APP_INITIALIZER that runs Keycloak before Angular starts
 * - Bootstraps the main AppComponent
 */

import { NgModule, APP_INITIALIZER } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { KeycloakAngularModule, KeycloakService } from 'keycloak-angular';
import { initializeKeycloak } from './keycloak-init.factory';

import { AppComponent } from './app.component';

@NgModule({
  declarations: [
    AppComponent // Main application UI component
  ],
  imports: [
    BrowserModule,
    KeycloakAngularModule  // Enables Keycloak features in Angular
  ],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: initializeKeycloak, // Run our Keycloak init BEFORE Angular loads
      deps: [KeycloakService],
      multi: true
    }
  ],
  bootstrap: [AppComponent] // Application starts from this component
})
export class AppModule { }
