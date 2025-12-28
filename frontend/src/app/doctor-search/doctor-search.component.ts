import { Component, OnInit, inject } from '@angular/core';
import { PatientDoctorService } from '../core/services/patient-doctor.service';
import { AuthService } from '../core/services/auth.service';
import { AddDoctorHelper } from '../core/services/add-doctor-helper';
import { UserContextService } from '../core/services/user-context.service';
import { LoggingService } from '../core/services/logging.service';

interface Doctor {
  doctorId: string;
  firstName: string;
  lastName: string;
  medicalOrganization: string;
  publicKeyPEM?: string;
}

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

  private patientDoctorService = inject(PatientDoctorService);
  private auth = inject(AuthService);
  private userContext = inject(UserContextService);
  private addDoctorHelper = inject(AddDoctorHelper);
  private logger = inject(LoggingService);

  ngOnInit(): void {
    this.loadAll();
  }

  async onSearch() {
    this.message = null;
    this.error = null;
    this.loading = true;
    const term = this.searchTerm.trim();
    this.logger.debug('Searching doctors', { term: term || '(all)' }, 'DoctorSearchComponent');
    try {
      const res = term
        ? await this.patientDoctorService.searchDoctors(term).toPromise()
        : await this.patientDoctorService.listAllDoctors().toPromise();
      this.doctors = (res?.doctors ?? []) as Doctor[];
      this.logger.info('Doctor search returned results', { count: this.doctors.length }, 'DoctorSearchComponent');
    } catch (e: unknown) {
      this.error = 'Erreur pendant la recherche des médecins';
      const msg = e instanceof Error ? e.message : String(e);
      this.logger.error('Doctor search failed', e, { message: msg }, 'DoctorSearchComponent');
      // replaced console.error with structured logging
    } finally {
      this.loading = false;
    }
  }

  async addDoctor(doctor: Doctor) {
    this.message = null;
    this.error = null;
    this.addingId = doctor.doctorId;
    this.logger.debug('Adding doctor', { doctorId: doctor.doctorId }, 'DoctorSearchComponent');
    try {
      const keycloakId = this.auth.sub;
      const patientUserId = this.userContext.userId;
      if (!patientUserId) {
        throw new Error('Patient user ID not found');
      }
      await this.addDoctorHelper.addDoctorToPatient(doctor.doctorId, patientUserId, keycloakId);
      this.message = `Médecin ajouté: ${doctor.firstName} ${doctor.lastName}`;
      this.logger.logAction('DOCTOR_ADDED_VIA_SEARCH', '', {
        doctorId: doctor.doctorId,
        doctorName: `${doctor.firstName} ${doctor.lastName}`,
        organization: doctor.medicalOrganization
      });
    } catch (e: unknown) {
      const msg = this.extractMessage(e);
      this.error = msg || 'Échec de l\'ajout du médecin';
      this.logger.error('Failed to add doctor', e, { doctorId: doctor.doctorId, message: msg }, 'DoctorSearchComponent');
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
    } catch (e: unknown) {
      this.error = 'Impossible de charger la liste des médecins';
      const msg = this.extractMessage(e);
      this.logger.error('Failed to load doctors list', e, { message: msg }, 'DoctorSearchComponent');
    } finally {
      this.loading = false;
    }
  }

  private extractMessage(err: unknown): string {
    if (typeof err === 'object' && err !== null && 'message' in err) {
      const m = (err as { message?: unknown }).message;
      return typeof m === 'string' ? m : String(m);
    }
    return String(err);
  }
}
