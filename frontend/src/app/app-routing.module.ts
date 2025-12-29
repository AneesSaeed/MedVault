import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './home/home.component';
import { OnboardingComponent } from './onboarding/onboarding.component';
import { MyPatientsComponent } from './my-patients/my-patients.component';
import { MyDoctorsComponent } from './my-doctors/my-doctors.component';
import { PendingMedicalFilesComponent } from './pending-medical-files/pending-medical-files.component';

const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'onboarding', component: OnboardingComponent },
  { path: 'my-doctors', component: MyDoctorsComponent },
  { path: 'my-patients', component: MyPatientsComponent },
  { path: 'pending-files', component: PendingMedicalFilesComponent },
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
