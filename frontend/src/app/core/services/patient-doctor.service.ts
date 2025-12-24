import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import { environment } from "../../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class PatientDoctorService {
  private api = environment.apiBaseUrl;
  private http = inject(HttpClient);

  /**
   * Liste tous les médecins disponibles
   */
  listAllDoctors() {
    return this.http.get<any>(`${this.api}/patient-doctor/doctors`);
  }

  /**
   * Recherche des médecins par nom/prénom
   */
  searchDoctors(searchTerm: string) {
    return this.http.get<any>(`${this.api}/patient-doctor/doctors`, {
      params: { search: searchTerm }
    });
  }

  /**
   * Récupère la clé publique RSA d'un médecin
   */
  getDoctorPublicKey(doctorId: string) {
    return this.http.get<any>(`${this.api}/patient-doctor/doctor/${doctorId}/public-key`);
  }

  /**
   * Récupère la clé publique RSA d'un patient
   * Permet au docteur de chiffrer la clé AES temporaire avant de proposer l'upload
   * @param patientId ID du patient
   */
  getPatientPublicKey(patientId: string) {
    return this.http.get<{ publicKeyPEM: string }>(`${this.api}/patient-doctor/patient/${patientId}/public-key`);
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
   * Récupère la liste des médecins associés au patient actuel
   */
  getMyDoctors() {
    return this.http.get<any>(`${this.api}/patient-doctor/my-doctors`);
  }

  /**
   * Récupère la liste des patients du médecin actuellement authentifié
   */
  getMyPatients() {
    return this.http.get<any>(`${this.api}/patient-doctor/my-patients`);
  }

  /**
   * Récupère les données chiffrées d'un patient spécifique
   * @param patientId ID du patient
   * @returns Données chiffrées: firstName, lastName, email, dob + clé AES chiffrée
   */
  getPatientData(patientId: string) {
    return this.http.get<any>(`${this.api}/patient-doctor/patient/${patientId}/data`);
  }

  /**
   * Supprime un médecin de la liste des médecins du patient actuel
   */
  removeDoctorFromPatient(doctorId: string) {
    return this.http.delete(`${this.api}/patient-doctor/remove/${doctorId}`);
  }
}

