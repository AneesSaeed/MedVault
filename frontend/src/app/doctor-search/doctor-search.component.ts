import { Component, OnInit } from '@angular/core';
import { PatientDoctorService } from '../core/services/patient-doctor.service';
import { CryptoService } from '../core/services/crypto.service';
import { AuthService } from '../core/services/auth.service';
import { MedicalFilesApi } from '../core/api/medical-files.api';
import { AddDoctorHelper } from '../core/services/add-doctor-helper';

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
  styleUrls: ['./doctor-search.component.scss']
})
export class DoctorSearchComponent implements OnInit {
  searchTerm = '';
  loading = false;
  addingId: string | null = null;
  message: string | null = null;
  error: string | null = null;
  doctors: Doctor[] = [];

  private helper: AddDoctorHelper;

  constructor(
    private patientDoctorService: PatientDoctorService,
    private cryptoService: CryptoService,
    private medicalFilesApi: MedicalFilesApi,
    private auth: AuthService
  ) {
    this.helper = new AddDoctorHelper(this.cryptoService, this.patientDoctorService, this.medicalFilesApi);
  }

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
      await this.helper.addDoctorToPatient(doctor.doctorId, keycloakId);
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
