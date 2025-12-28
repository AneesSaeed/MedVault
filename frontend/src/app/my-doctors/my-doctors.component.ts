import { Component, OnInit } from '@angular/core';
import { PatientDoctorService } from '../core/services/patient-doctor.service';
import { LoggingService } from '../core/services/logging.service';

type Doctor = {
  doctorId: string;
  firstName: string;
  lastName: string;
  medicalOrganization: string;
  email: string;
};

@Component({
    selector: 'app-my-doctors',
    templateUrl: './my-doctors.component.html',
    styleUrls: ['./my-doctors.component.scss'],
    standalone: false
})
export class MyDoctorsComponent implements OnInit {
  loading = false;
  error: string | null = null;
  message: string | null = null;
  removingId: string | null = null;

  doctors: Doctor[] = [];

  constructor(
    private service: PatientDoctorService,
    private logger: LoggingService
  ) {}

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
    } catch (e: any) {
      this.error = 'Impossible de charger la liste de vos médecins';
      this.logger.error('Failed to load my doctors: {}', e.message || e);
    } finally {
      this.loading = false;
    }
  }

  async removeDoctor(doctor: Doctor) {
    if (!confirm(`Retirer ${doctor.firstName} ${doctor.lastName} de votre liste de médecins ?`)) {
      return;
    }

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
      // Recharger la liste
      await this.loadMyDoctors();
    } catch (e: any) {
      this.error = e?.error?.error || 'Échec de la suppression du médecin';
      this.logger.error('Failed to remove doctor {}: {}', doctor.doctorId, e?.error?.error || e.message);
    } finally {
      this.removingId = null;
    }
  }
}
