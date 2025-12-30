import { Component, OnInit, Type, inject } from '@angular/core';
import { PatientDoctorService } from '../core/services/patient-doctor.service';
import { PatientDataService } from '../core/services/patient-data.service';
import { AuthService } from '../core/services/auth.service';
import { LoggingService } from '../core/services/logging.service';

import { PatientRecordModalComponent, PatientRecordModalData } from '../patient-record-modal/patient-record-modal.component';
import { BaseModalComponent } from '../shared/modal/base-modal/base-modal.component';

interface RawPatient {
  patientId: string;
}

interface DecryptedPatient {
  patientId: string;
  firstName: string;
  lastName: string;
  email: string;
  dob?: string;
}

import { CommonModule } from '@angular/common';
@Component({
  selector: 'app-my-patients',
  templateUrl: './my-patients.component.html',
  styleUrls: ['./my-patients.component.scss'],
  standalone: true,
  imports: [CommonModule, BaseModalComponent]
})
export class MyPatientsComponent implements OnInit {
  loading = false;
  error: string | null = null;

  patients: DecryptedPatient[] = [];

  // modal
  modalOpen = false;
  modalTitle = '';
  modalComponent: Type<unknown> = PatientRecordModalComponent;
  modalData!: PatientRecordModalData;

  private readonly service = inject(PatientDoctorService);
  private readonly patientDataService = inject(PatientDataService);
  private readonly auth = inject(AuthService);
  private readonly logger = inject(LoggingService);

  async ngOnInit(): Promise<void> {
    await this.loadAndDecryptPatients();
  }

  private async loadAndDecryptPatients(): Promise<void> {
    this.loading = true;
    this.error = null;

    try {
      const res = await this.service.getMyPatients().toPromise();
      const list: RawPatient[] = (res?.patients ?? []) as RawPatient[];

      const decrypted: DecryptedPatient[] = [];

      for (const item of list) {
        try {
          const patientData = await this.patientDataService.getPatientData(
            item.patientId,
            this.auth.sub
          );

          decrypted.push({
            patientId: item.patientId,
            firstName: patientData.firstName,
            lastName: patientData.lastName,
            email: patientData.email,
            dob: patientData.dateOfBirth
          });
        } catch (e: unknown) {
          const error = e as { message?: string };
          const errorMessage = error?.message || String(e);
          this.logger.error('Failed to decrypt patient', e, { patientId: item.patientId, errorMessage });
          decrypted.push({
            patientId: item.patientId,
            firstName: '(erreur déchiffrement)',
            lastName: '',
            email: ''
          });
        }
      }

      this.patients = decrypted;
      this.logger.logAction('MY_PATIENTS_LOADED', '', { patientsCount: this.patients.length });
    } catch (e: unknown) {
      const error = e as { message?: string };
      const errorMessage = error?.message || 'Impossible de charger les patients';
      this.error = errorMessage;
      this.logger.error('Failed to load my patients', e, { errorMessage });
    } finally {
      this.loading = false;
    }
  }

  openPatient(p: DecryptedPatient): void {
    this.modalTitle = `Dossier patient`;
    this.modalData = {
      patientId: p.patientId,
      firstName: p.firstName,
      lastName: p.lastName,
      email: p.email,
      dob: p.dob
    };
    this.modalOpen = true;
  }

  onModalClosed(): void {
    this.modalOpen = false;
  }
}
