import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { PatientDoctorService } from '../core/services/patient-doctor.service';
import { AuthService } from '../core/services/auth.service';
import { AddDoctorHelper } from '../core/services/add-doctor-helper';
import { UserContextService } from '../core/services/user-context.service';
import { LoggingService } from '../core/services/logging.service';
import { ModalRef } from '../shared/modal/base-modal/modal-ref';

interface Doctor {
  doctorId: string;
  firstName: string;
  lastName: string;
  medicalOrganization: string;
}

@Component({
  selector: 'app-doctor-search-modal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './doctor-search-modal.component.html',
  styleUrls: ['./doctor-search-modal.component.scss'],
})
export class DoctorSearchModalComponent implements OnInit {
  searchTerm = '';
  loading = false;
  addingId: string | null = null;
  message: string | null = null;
  error: string | null = null;
  doctors: Doctor[] = [];

  /** doctors already added by the patient */
  private myDoctorIds = new Set<string>();

  private patientDoctorService = inject(PatientDoctorService);
  private auth = inject(AuthService);
  private userContext = inject(UserContextService);
  private addDoctorHelper = inject(AddDoctorHelper);
  private logger = inject(LoggingService);
  private modalRef = inject(ModalRef);

  ngOnInit(): void {
    this.init();
  }

  isAlreadyAdded(doctorId: string): boolean {
    return this.myDoctorIds.has(doctorId);
  }

  async onSearch(): Promise<void> {
    this.message = null;
    this.error = null;
    this.loading = true;

    const term = this.searchTerm.trim();
    this.logger.debug('Searching doctors', { term: term || '(all)' }, 'DoctorSearchModalComponent');

    try {
      const res = term
        ? await this.patientDoctorService.searchDoctors(term).toPromise()
        : await this.patientDoctorService.listAllDoctors().toPromise();

      this.doctors = (res?.doctors ?? []) as Doctor[];
    } catch (e: unknown) {
      this.error = 'Erreur pendant la recherche des médecins';
      this.logger.error('Doctor search failed', e, {}, 'DoctorSearchModalComponent');
    } finally {
      this.loading = false;
    }
  }

  async addDoctor(doctor: Doctor): Promise<void> {
    if (this.isAlreadyAdded(doctor.doctorId)) return;

    this.message = null;
    this.error = null;
    this.addingId = doctor.doctorId;

    try {
      const keycloakId = this.auth.sub;
      const patientUserId = this.userContext.userId;
      if (!patientUserId) throw new Error('Patient user ID not found');

      await this.addDoctorHelper.addDoctorToPatient(doctor.doctorId, patientUserId, keycloakId);

      // keep UI consistent even if modal stays open briefly
      this.myDoctorIds.add(doctor.doctorId);

      // close and notify parent to refresh list
      this.modalRef.close({ added: true, doctorId: doctor.doctorId });
    } catch (e: unknown) {
      this.error = 'Échec de l\'ajout du médecin';
      this.logger.error('Failed to add doctor', e, { doctorId: doctor.doctorId }, 'DoctorSearchModalComponent');
    } finally {
      this.addingId = null;
    }
  }

  private async init(): Promise<void> {
    this.loading = true;
    this.error = null;

    try {
      // load my doctors first (for "already added" state)
      const my = await this.patientDoctorService.getMyDoctors().toPromise();
      const ids = my?.doctorIds ?? [];
      this.myDoctorIds = new Set(ids);

      // then load all
      const res = await this.patientDoctorService.listAllDoctors().toPromise();
      this.doctors = (res?.doctors ?? []) as Doctor[];
    } catch {
      this.error = 'Impossible de charger la liste des médecins';
      this.doctors = [];
    } finally {
      this.loading = false;
    }
  }
}
