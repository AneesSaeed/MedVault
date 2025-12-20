import { Component, OnInit } from '@angular/core';
import { PatientDoctorService } from '../core/services/patient-doctor.service';

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
  styleUrls: ['./my-doctors.component.scss']
})
export class MyDoctorsComponent implements OnInit {
  loading = false;
  error: string | null = null;
  message: string | null = null;
  removingId: string | null = null;

  doctors: Doctor[] = [];

  constructor(private service: PatientDoctorService) {}

  async ngOnInit() {
    await this.loadMyDoctors();
  }

  private async loadMyDoctors() {
    this.loading = true;
    this.error = null;
    try {
      // 1. Récupérer les IDs des médecins du patient
      const myDoctorsRes = await this.service.getMyDoctors().toPromise();
      const doctorIds: string[] = myDoctorsRes?.doctorIds ?? [];

      if (doctorIds.length === 0) {
        this.doctors = [];
        return;
      }

      // 2. Récupérer la liste complète des médecins
      const allDoctorsRes = await this.service.listAllDoctors().toPromise();
      const allDoctors: Doctor[] = (allDoctorsRes?.doctors ?? []) as Doctor[];

      // 3. Filtrer pour ne garder que mes médecins
      this.doctors = allDoctors.filter(d => doctorIds.includes(d.doctorId));
    } catch (e: any) {
      console.error(e);
      this.error = 'Impossible de charger la liste de vos médecins';
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

    try {
      await this.service.removeDoctorFromPatient(doctor.doctorId).toPromise();
      this.message = `Médecin retiré : ${doctor.firstName} ${doctor.lastName}`;
      // Recharger la liste
      await this.loadMyDoctors();
    } catch (e: any) {
      console.error(e);
      this.error = e?.error?.error || 'Échec de la suppression du médecin';
    } finally {
      this.removingId = null;
    }
  }
}
