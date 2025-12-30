import { Component, OnInit, Type, inject } from '@angular/core';
import { PatientDoctorService } from '../core/services/patient-doctor.service';
import { LoggingService } from '../core/services/logging.service';
import { DoctorSearchModalComponent } from '../doctor-search-modal/doctor-search-modal.component';
import { BaseModalComponent } from '../shared/modal/base-modal/base-modal.component';

interface Doctor {
  doctorId: string;
  firstName: string;
  lastName: string;
  medicalOrganization: string;
  email: string;
}

type AddDoctorModalResult = { added?: boolean; doctorId?: string } | undefined;

import { CommonModule } from '@angular/common';
@Component({
  selector: 'app-my-doctors',
  templateUrl: './my-doctors.component.html',
  styleUrls: ['./my-doctors.component.scss'],
  standalone: true,
  imports: [CommonModule, BaseModalComponent]
})
export class MyDoctorsComponent implements OnInit {
  loading = false;
  error: string | null = null;
  message: string | null = null;
  removingId: string | null = null;

  doctors: Doctor[] = [];

  modalOpen = false;
  modalTitle = 'Ajouter un médecin';
  modalComponent: Type<unknown> = DoctorSearchModalComponent;
  modalData: Record<string, unknown> = {};

  private readonly service = inject(PatientDoctorService);
  private readonly logger = inject(LoggingService);

  async ngOnInit() {
    await this.loadMyDoctors();
  }

  private async loadMyDoctors() {
    this.loading = true;
    this.error = null;
    this.logger.debug('Loading my doctors list');

    try {
      // 1. Récupérer les IDs des médecins du patient
      const myDoctorsRes = await this.service.getMyDoctors().toPromise();
      const doctorIds: string[] = myDoctorsRes?.doctorIds ?? [];

      if (doctorIds.length === 0) {
        this.doctors = [];
        this.logger.info('No doctors found for patient');
        return;
      }

      // 2. Récupérer la liste complète des médecins
      const allDoctorsRes = await this.service.listAllDoctors().toPromise();
      const allDoctors: Doctor[] = (allDoctorsRes?.doctors ?? []) as Doctor[];

      // 3. Filtrer pour ne garder que mes médecins
      this.doctors = allDoctors.filter(d => doctorIds.includes(d.doctorId));
      this.logger.logAction('MY_DOCTORS_LOADED', '', { doctorsCount: this.doctors.length });
    } catch (e: unknown) {
      this.error = 'Impossible de charger la liste de vos médecins';
      const error = e as { message?: string };
      const errorMessage = error?.message || String(e);
      this.logger.error('Failed to load my doctors', e, { errorMessage });
    } finally {
      this.loading = false;
    }
  }

  async removeDoctor(doctor: Doctor) {
    if (!confirm(`Retirer ${doctor.firstName} ${doctor.lastName} de votre liste de médecins ?`)) return;

    this.removingId = doctor.doctorId;
    this.message = null;
    this.error = null;
    this.logger.debug('Removing doctor', { doctorId: doctor.doctorId }, 'MyDoctorsComponent');

    try {
      await this.service.removeDoctorFromPatient(doctor.doctorId).toPromise();

      this.message = `Médecin retiré : ${doctor.firstName} ${doctor.lastName}`;
      this.logger.logAction('DOCTOR_REMOVED', '', {
        doctorId: doctor.doctorId,
        doctorName: `${doctor.firstName} ${doctor.lastName}`
      });

      await this.loadMyDoctors();
    } catch (e: unknown) {
      const error = e as { error?: { error?: string }; message?: string };
      const errorMessage = error?.error?.error || error?.message || 'Échec de la suppression du médecin';
      this.error = errorMessage;
      this.logger.error('Failed to remove doctor', e, { doctorId: doctor.doctorId, errorMessage });
    } finally {
      this.removingId = null;
    }
  }

  openAddDoctor(): void {
    this.modalOpen = true;
  }

  onModalClosed(result?: unknown): void {
    this.modalOpen = false;
    const modalResult = result as AddDoctorModalResult | undefined;
    if (modalResult?.added) {
      this.message = 'Médecin ajouté.';
      this.loadMyDoctors();
    }
  }
}
