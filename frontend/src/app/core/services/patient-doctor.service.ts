import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";

@Injectable({
  providedIn: 'root'
})
export class PatientDoctorService {
  private api = 'http://localhost:8081/api'

  constructor(private http: HttpClient) {}

  /**
   * Liste tous les médecins disponibles
   */
  listAllDoctors() {
    return this.http.get<any>(`${this.api}/patient-doctor/doctors`);
  }

  /**
   * Récupère la clé publique RSA d'un médecin
   */
  getDoctorPublicKey(doctorId: string) {
    return this.http.get<any>(`${this.api}/patient-doctor/doctor/${doctorId}/public-key`);
  }

  /**
   * Ajoute un médecin à la liste des médecins du patient actuel
   */
  addDoctorToPatient(payload: {
    doctorId: string;
    encryptedPatientAESKeyBase64: string;
  }) {
    return this.http.post(`${this.api}/patient-doctor/add`, payload);
  }

  /**
   * Recherche des patients par nom et/ou prénom
   */
  searchPatients(firstName?: string, lastName?: string) {
    const params: any = {};
    if (firstName) params.firstName = firstName;
    if (lastName) params.lastName = lastName;
    return this.http.get<any>(`${this.api}/patient-doctor/patients/search`, { params });
  }

  /**
   * Récupère la clé publique RSA d'un patient
   */
  getPatientPublicKey(patientId: string) {
    return this.http.get<any>(`${this.api}/patient-doctor/patient/${patientId}/public-key`);
  }

  /**
   * Récupère la liste des médecins associés au patient actuel
   */
  getMyDoctors() {
    return this.http.get<any>(`${this.api}/patient-doctor/my-doctors`);
  }

  /**
   * Supprime un médecin de la liste des médecins du patient actuel
   */
  removeDoctorFromPatient(doctorId: string) {
    return this.http.delete(`${this.api}/patient-doctor/remove/${doctorId}`);
  }
}

