import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * DTO pour créer une demande d'upload de fichier médical
 * Le docteur crée une clé AES temporaire, chiffre le fichier avec,
 * puis chiffre cette clé avec la clé publique RSA du patient
 */
export interface CreatePendingMedicalFileRequest {
  fileNameEncBase64: string;           // Nom du fichier chiffré avec Ktemp
  contentEncBase64: string;            // Contenu du fichier chiffré avec Ktemp
  ivBase64: string;                    // IV pour AES-GCM
  wrappedTempKeyForPatientBase64: string; // Ktemp chiffré avec la clé publique RSA du patient
  mimeTypeEncBase64: string;           // Type MIME chiffré (optionnel)
}

/**
 * DTO retourné au patient: demande d'upload en attente
 */
export interface PendingMedicalFileInfo {
  id: string;                          // UUID de la demande
  uploaderDoctorId: string;            // UUID du docteur
  fileNameEncBase64: string;           // Chiffré avec Ktemp
  contentEncBase64: string;            // Chiffré avec Ktemp
  ivBase64: string;                    // IV pour AES-GCM
  wrappedTempKeyForPatientBase64: string; // Ktemp chiffré avec RSA du patient
  mimeTypeEncBase64: string;           // Type MIME chiffré
  createdAt: string;                   // ISO timestamp
}

@Injectable({ providedIn: 'root' })
export class PendingMedicalFilesApi {
  private readonly baseUrl = environment.apiBaseUrl;

  private http = inject(HttpClient);

  /**
   * DOCTOR: Crée une demande d'upload de fichier pour un patient
   * Le docteur a déjà chiffré le fichier avec sa clé AES temporaire
   * et enveloppé cette clé avec la clé publique RSA du patient
   */
  createRequest(
    patientId: string,
    payload: CreatePendingMedicalFileRequest
  ): Observable<{ id: string | null }> {
    return this.http
      .post(
        `${this.baseUrl}/patient/${patientId}/file-requests`,
        payload,
        { responseType: 'text' }
      )
      .pipe(
        map((text) => {
          // Backend may return JSON or plain text; tolerate both to avoid JSON parse SyntaxError
          try {
            const parsed = JSON.parse(text as unknown as string) as { id?: string };
            if (parsed && typeof parsed === 'object' && 'id' in parsed) {
              return { id: parsed.id ?? null };
            }
          } catch {
            // not JSON, fall back to plain text
          }
          return { id: text ? String(text) : null };
        })
      );
  }

  /**
   * PATIENT: Récupère les demandes d'upload en attente
   * Les données restent chiffrées côté serveur
   */
  listPendingRequests(): Observable<PendingMedicalFileInfo[]> {
    return this.http.get<PendingMedicalFileInfo[]>(
      `${this.baseUrl}/patient/me/file-requests`
    );
  }

  /**
   * PATIENT: Rejette une demande d'upload (la supprime)
   */
  rejectRequest(requestId: string): Observable<void> {
    return this.http.delete<void>(
      `${this.baseUrl}/patient/file-requests/${requestId}`
    );
  }
}
