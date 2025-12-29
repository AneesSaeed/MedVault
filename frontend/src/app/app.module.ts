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
import { HeaderComponent } from './header/header.component';
import { HomeComponent } from './home/home.component';
import { AppRoutingModule } from './app-routing.module';
import { OnboardingComponent } from './onboarding/onboarding.component';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { DoctorSearchComponent } from './doctor-search/doctor-search.component';
import { MyPatientsComponent } from './my-patients/my-patients.component';
import { MyDoctorsComponent } from './my-doctors/my-doctors.component';
import { PendingMedicalFilesComponent } from './pending-medical-files/pending-medical-files.component';
import { BaseModalComponent } from './shared/modal/base-modal/base-modal.component';

@NgModule({ declarations: [
        AppComponent, // Main application UI component
        HeaderComponent,
        HomeComponent,
        OnboardingComponent,
        DoctorSearchComponent,
        MyPatientsComponent,
        MyDoctorsComponent,
        PendingMedicalFilesComponent,
    ],
    bootstrap: [AppComponent] // Application starts from this component
    , imports: [BrowserModule,
        KeycloakAngularModule, // Enables Keycloak features in Angular
        AppRoutingModule,
        FormsModule,
        BaseModalComponent
      ], providers: [
        {
            provide: APP_INITIALIZER,
            useFactory: initializeKeycloak, // Run our Keycloak init BEFORE Angular loads
            deps: [KeycloakService],
            multi: true
        },
        provideHttpClient(withInterceptorsFromDi())
    ] })
export class AppModule { }
