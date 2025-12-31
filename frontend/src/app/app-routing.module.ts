import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () => import('./home/home.component').then(m => m.HomeComponent) },
  { path: 'onboarding', loadComponent: () => import('./onboarding/onboarding.component').then(m => m.OnboardingComponent) },
  { path: 'my-doctors', loadComponent: () => import('./my-doctors/my-doctors.component').then(m => m.MyDoctorsComponent) },
  { path: 'my-patients', loadComponent: () => import('./my-patients/my-patients.component').then(m => m.MyPatientsComponent) },
  { path: 'pending-files', loadComponent: () => import('./pending-medical-files/pending-medical-files.component').then(m => m.PendingMedicalFilesComponent) },
  { path: '**', redirectTo: '' },
];
