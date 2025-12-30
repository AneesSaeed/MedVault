import { Component, OnInit, Type } from '@angular/core';
import { PatientDoctorService } from '../core/services/patient-doctor.service';
import { PatientDataService } from '../core/services/patient-data.service';
import { AuthService } from '../core/services/auth.service';
import { LoggingService } from '../core/services/logging.service';

import { PatientRecordModalComponent, PatientRecordModalData } from '../patient-record-modal/patient-record-modal.component';

type RawPatient = { patientId: string; };

type DecryptedPatient = {
  patientId: string;
  firstName: string;
  lastName: string;
  email: string;
  dob?: string;
};

@Component({
  selector: 'app-my-patients',
  templateUrl: './my-patients.component.html',
  styleUrls: ['./my-patients.component.scss'],
  standalone: false
})
export class MyPatientsComponent implements OnInit {
  loading = false;
  error: string | null = null;

  patients: DecryptedPatient[] = [];

  // modal
  modalOpen = false;
  modalTitle = '';
  modalComponent: Type<any> = PatientRecordModalComponent;
  modalData!: PatientRecordModalData;

  constructor(
    private service: PatientDoctorService,
    private patientDataService: PatientDataService,
    private auth: AuthService,
    private logger: LoggingService
  ) {}

  async ngOnInit(): Promise<void> {
    await this.loadAndDecryptPatients();
  }

  private async loadAndDecryptPatients(): Promise<void> {
    this.loading = true;
    this.error = null;

    try {
      const res = await this.service.getMyPatients().toPromise();
      const list: RawPatient[] = (res?.patients ?? []) as any[];

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
        } catch (e: any) {
          this.logger.error('Failed to decrypt patient {}: {}', item.patientId, e?.message || e);
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
    } catch (e: any) {
      this.error = e?.message || 'Impossible de charger les patients';
      this.logger.error('Failed to load my patients: {}', e?.message || e);
    } finally {
      this.loading = false;
    }
  }

  openPatient(p: DecryptedPatient): void {
    console.log('openPatient', p.patientId);
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
