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
import { initializeKeycloak } from './core/keycloak/keycloak-init.factory';

import { AppComponent } from './app.component';
import { HeaderComponent } from './header/header.component';
import { HomeComponent } from './home/home.component';
import { AppRoutingModule } from './app-routing.module';

@NgModule({
  declarations: [
    AppComponent, // Main application UI component
    HeaderComponent, HomeComponent
  ],
  imports: [
    BrowserModule,
    KeycloakAngularModule,  // Enables Keycloak features in Angular
    AppRoutingModule
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
