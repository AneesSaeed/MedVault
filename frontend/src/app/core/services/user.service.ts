import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import { environment } from "../../../environments/environment";

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private api = environment.apiBaseUrl;
  private http = inject(HttpClient);

  userExists() {
    return this.http.get<boolean>(`${this.api}/user/exists`);
  }

  getMe() {
    return this.http.get<MeResponse>(`${this.api}/user/me`);
  }

  createPatient(payload: CreatePatientDTO) {
    return this.http.post(`${this.api}/patient`, payload)
  }

  createDoctor(payload: CreateDoctorDTO) {
    return this.http.post(`${this.api}/doctor`, payload)
  }
}

export interface MeResponse {
  keycloakId: string;
  userId?: string;
  role: 'PATIENT' | 'DOCTOR';
  firstName?: string;
  lastName?: string;
}

export interface CreatePatientDTO {
  // SÉCURITÉ: TOUS les champs doivent être chiffrés pour protéger la vie privée du patient
  firstNameEncBase64: string;
  lastNameEncBase64: string;
  emailEncBase64: string;
  dateOfBirthEncBase64: string;
  publicKeyPEM: string;
  symmetricKeyEncBase64: string;
}

export interface CreateDoctorDTO {
  // SÉCURITÉ: Les médecins ne chiffrent pas leurs données (ils sont découvrables)
  // Pas de clé AES - seule la clé RSA publique est stockée
  firstName: string;
  lastName: string;
  email: string;
  medicalOrganization: string;
  publicKeyPEM: string;
}
