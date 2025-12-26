import { Component, OnInit } from '@angular/core';
import { PatientDoctorService } from '../core/services/patient-doctor.service';
import { AuthService } from '../core/services/auth.service';
import { AddDoctorHelper } from '../core/services/add-doctor-helper';
import { UserContextService } from '../core/services/user-context.service';

type Doctor = {
  doctorId: string;
  firstName: string;
  lastName: string;
  medicalOrganization: string;
  publicKeyPEM?: string;
};

@Component({
    selector: 'app-doctor-search',
    templateUrl: './doctor-search.component.html',
    styleUrls: ['./doctor-search.component.scss'],
    standalone: false
})
export class DoctorSearchComponent implements OnInit {
  searchTerm = '';
  loading = false;
  addingId: string | null = null;
  message: string | null = null;
  error: string | null = null;
  doctors: Doctor[] = [];

  constructor(
    private patientDoctorService: PatientDoctorService,
    private auth: AuthService,
    private userContext: UserContextService,
    private addDoctorHelper: AddDoctorHelper
  ) {}

  ngOnInit(): void {
    this.loadAll();
  }

  async onSearch() {
    this.message = null;
    this.error = null;
    this.loading = true;
    try {
      const term = this.searchTerm.trim();
      const res = term
        ? await this.patientDoctorService.searchDoctors(term).toPromise()
        : await this.patientDoctorService.listAllDoctors().toPromise();
      this.doctors = (res?.doctors ?? []) as Doctor[];
    } catch (e: any) {
      this.error = 'Erreur pendant la recherche des médecins';
      console.error(e);
    } finally {
      this.loading = false;
    }
  }

  async addDoctor(doctor: Doctor) {
    this.message = null;
    this.error = null;
    this.addingId = doctor.doctorId;
    try {
      const keycloakId = this.auth.sub;
      const patientUserId = this.userContext.userId;
      if (!patientUserId) {
        throw new Error('Patient user ID not found');
      }
      await this.addDoctorHelper.addDoctorToPatient(doctor.doctorId, patientUserId, keycloakId);
      this.message = `Médecin ajouté: ${doctor.firstName} ${doctor.lastName}`;
    } catch (e: any) {
      this.error = e?.message || 'Échec de l\'ajout du médecin';
      console.error(e);
    } finally {
      this.addingId = null;
    }
  }

  private async loadAll() {
    this.loading = true;
    this.error = null;
    try {
      const res = await this.patientDoctorService.listAllDoctors().toPromise();
      this.doctors = (res?.doctors ?? []) as Doctor[];
    } catch (e: any) {
      this.error = 'Impossible de charger la liste des médecins';
      console.error(e);
    } finally {
      this.loading = false;
    }
  }
}
