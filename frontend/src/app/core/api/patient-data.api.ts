import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Modèle pour les données d'un patient avec sa clé symétrique chiffrée.
 */
export interface PatientDataModel {
  patientId: string;
  firstNameEncBase64: string;
  lastNameEncBase64: string;
  emailEncBase64: string;
  dateOfBirthEncBase64: string;
  symmetricKeyEncBase64: string;
}

@Injectable({ providedIn: 'root' })
export class PatientDataApi {
  private readonly baseUrl = `${environment.apiBaseUrl}/patient`;

  private http = inject(HttpClient);

  /**
   * Récupère les données d'un patient avec sa clé symétrique chiffrée.
   *
   * Utilisé par:
   * - Un patient pour consulter ses propres données
   * - Un docteur pour consulter les données d'un patient (auquel il a accès)
   *
   * @param patientId ID UUID du patient
   * @returns Observable avec données chiffrées + clé symétrique chiffrée
   */
  getPatientData(patientId: string): Observable<PatientDataModel> {
    return this.http.get<PatientDataModel>(`${this.baseUrl}/${patientId}/data`);
  }
}
