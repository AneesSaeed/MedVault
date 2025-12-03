import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { UserService } from '../core/services/user.service';

@Component({
  selector: 'app-onboarding',
  templateUrl: './onboarding.component.html',
  styleUrls: ['./onboarding.component.scss']
})
export class OnboardingComponent {

  role: 'PATIENT' | 'DOCTOR' | null = null;
  dateOfBirth = '';
  medicalOrg = '';

  constructor(
    public auth: AuthService,
    private userService: UserService,
    private router: Router
  ) {}

  selectRole(r: 'PATIENT' | 'DOCTOR') {
    this.role = r;
  }

  submit() {
    if (!this.role) return;

    const userPayload = {
      keycloakId: this.auth.sub,
      firstNameEnc: btoa(this.auth.firstName),
      lastNameEnc:  btoa(this.auth.lastName),
      emailEnc:     btoa(this.auth.email),
      role: this.role
    };

    if (this.role === 'PATIENT') {
      const payload = {
        user: userPayload,
        dateOfBirthEncBase64: btoa(this.dateOfBirth)
      };

      this.userService.createPatient(payload)
        .subscribe(() => this.router.navigate(['/']));
    }

    if (this.role === 'DOCTOR') {
      const payload = {
        user: userPayload,
        medicalOrganizationEncBase64: btoa(this.medicalOrg)
      };

      this.userService.createDoctor(payload)
        .subscribe(() => this.router.navigate(['/']));
    }
  }

  isFormValid(): boolean {
    if (this.role === 'PATIENT') {
      return !!this.dateOfBirth; // must not be empty
    }

    if (this.role === 'DOCTOR') {
      return !!this.medicalOrg && this.medicalOrg.trim().length > 0;
    }

    return false;
  }
}
