import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import { environment } from "../../../environments/environment";
import { LoggingService } from './logging.service';
import { Observable } from 'rxjs';

export interface DoctorInfo {
  doctorId: string;
  firstName: string;
  lastName: string;
  medicalOrganization: string;
  publicKeyPEM?: string;
}

export interface DoctorsResponse { doctors: DoctorInfo[] }
export interface MyDoctorsResponse { doctorIds: string[] }

export interface PatientEncryptedSummary {
  patientId: string;
  firstNameEnc: string;
  lastNameEnc: string;
  emailEnc: string;
  encryptedAESKey: string;
}

export interface MyPatientsResponse { patients: PatientEncryptedSummary[] }
export interface PublicKeyResponse { publicKeyPEM: string }

@Injectable({
  providedIn: 'root'
})
export class PatientDoctorService {
  private api = environment.apiBaseUrl;
  private http = inject(HttpClient);
  private logger = inject(LoggingService);

  /**
   * Liste tous les médecins disponibles
   */
  listAllDoctors() {
    this.logger.debug('Listing all doctors', {}, 'PatientDoctorService');
    return this.http.get<DoctorsResponse>(`${this.api}/patient-doctor/doctors`);
  }

  /**
   * Recherche des médecins par nom/prénom
   */
  searchDoctors(searchTerm: string) {
    this.logger.debug('Searching doctors', { searchTerm }, 'PatientDoctorService');
    return this.http.get<DoctorsResponse>(`${this.api}/patient-doctor/doctors`, {
      params: { search: searchTerm }
    });
  }

  /**
   * Récupère la clé publique RSA d'un médecin
   */
  getDoctorPublicKey(doctorId: string) {
    this.logger.debug('Getting public key for doctor', { doctorId }, 'PatientDoctorService');
    return this.http.get<PublicKeyResponse>(`${this.api}/patient-doctor/doctor/${doctorId}/public-key`);
  }

  /**
   * Récupère la clé publique RSA d'un patient
   * Permet au docteur de chiffrer la clé AES temporaire avant de proposer l'upload
   * @param patientId ID du patient
   */
  getPatientPublicKey(patientId: string) {
    this.logger.debug('Getting public key for patient', { patientId }, 'PatientDoctorService');
    return this.http.get<PublicKeyResponse>(`${this.api}/patient-doctor/patient/${patientId}/public-key`);
  }

  /**
   * Ajoute un médecin à la liste des médecins du patient actuel
   */
  addDoctorToPatient(payload: {
    doctorId: string;
    encryptedPatientAESKeyBase64: string;
  }) {
    this.logger.debug('Adding doctor to patient', { doctorId: payload.doctorId }, 'PatientDoctorService');
    return this.http.post(`${this.api}/patient-doctor/add`, payload);
  }

  /**
   * Récupère la liste des médecins associés au patient actuel
   */
  getMyDoctors() {
    this.logger.debug('Getting my doctors', {}, 'PatientDoctorService');
    return this.http.get<MyDoctorsResponse>(`${this.api}/patient-doctor/my-doctors`);
  }

  /**
   * Récupère la liste des patients du médecin actuellement authentifié
   */
  getMyPatients() {
    this.logger.debug('Getting my patients', {}, 'PatientDoctorService');
    return this.http.get<MyPatientsResponse>(`${this.api}/patient-doctor/my-patients`);
  }

  /**
   * Récupère les données chiffrées d'un patient spécifique
   * @param patientId ID du patient
   * @returns Données chiffrées: firstName, lastName, email, dob + clé AES chiffrée
   */
  getPatientData(patientId: string) {
    this.logger.debug('Getting patient data', { patientId }, 'PatientDoctorService');
    return this.http.get(`${this.api}/patient-doctor/patient/${patientId}/data`) as Observable<Record<string, unknown>>;
  }

  /**
   * Supprime un médecin de la liste des médecins du patient actuel
   */
  removeDoctorFromPatient(doctorId: string) {
    return this.http.delete(`${this.api}/patient-doctor/remove/${doctorId}`);
  }
}

