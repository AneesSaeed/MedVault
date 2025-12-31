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
import { FormsModule } from '@angular/forms';

import { KeycloakAngularModule, KeycloakService } from 'keycloak-angular';
import { initializeKeycloak } from './core/keycloak/keycloak-init.factory';

import { AppComponent } from './app.component';
import { HomeComponent } from './home/home.component';
import { AppRoutingModule } from './app-routing.module';
import { OnboardingComponent } from './onboarding/onboarding.component';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { MyPatientsComponent } from './my-patients/my-patients.component';
import { MyDoctorsComponent } from './my-doctors/my-doctors.component';
import { BaseModalComponent } from './shared/modal/base-modal/base-modal.component';

@NgModule({
    declarations: [
        // Les composants standalone ne doivent pas être dans declarations
    ],
    imports: [
        BrowserModule,
        KeycloakAngularModule, // Enables Keycloak features in Angular
        AppRoutingModule,
        FormsModule,
        BaseModalComponent,
        AppComponent, // Ajouté ici car standalone
        HomeComponent, // Standalone component
        OnboardingComponent, // Standalone component
        MyPatientsComponent, // Standalone component
        MyDoctorsComponent, // Standalone component
    ],
    bootstrap: [AppComponent], // Application starts from this component
    providers: [
        {
            provide: APP_INITIALIZER,
            useFactory: initializeKeycloak, // Run our Keycloak init BEFORE Angular loads
            deps: [KeycloakService],
            multi: true
        },
        provideHttpClient(withInterceptorsFromDi())
    ]
})
export class AppModule { }
