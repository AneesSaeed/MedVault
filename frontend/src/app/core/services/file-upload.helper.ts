import { Injectable, inject } from '@angular/core';
import { CryptoService } from './crypto.service';
import { PatientDoctorService } from './patient-doctor.service';
import { PendingMedicalFilesApi, CreatePendingMedicalFileRequest } from '../api/pending-medical-files.api';
import { toBase64 } from '../utils/base64.util';

/**
 * Service helper pour gérer l'upload sécurisé de fichiers médicaux par les docteurs
 *
 * Flux:
 * 1. Docteur sélectionne un patient et un fichier
 * 2. Service récupère la clé publique RSA du patient
 * 3. Génère une clé AES temporaire (Ktemp)
 * 4. Chiffre le fichier avec Ktemp
 * 5. Enveloppe Ktemp avec la clé publique RSA du patient
 * 6. Envoie la demande au serveur
 * 7. Patient reçoit la demande et peut l'accepter (le fichier devient alors permanent)
 */
@Injectable({
  providedIn: 'root'
})
export class FileUploadHelper {
  private readonly crypto = inject(CryptoService);
  private readonly patientDoctorService = inject(PatientDoctorService);
  private readonly pendingFilesApi = inject(PendingMedicalFilesApi);

  private extractErrorMessage(error: unknown): string {
    if (error && typeof error === 'object') {
      const errObj = error as any;
      if (errObj.error?.error) return String(errObj.error.error);
      if (errObj.error?.message) return String(errObj.error.message);
      if (errObj.message) return String(errObj.message);
      if (errObj.status && errObj.statusText) {
        return `${errObj.status} ${errObj.statusText}`;
      }
    }
    return String(error);
  }

  /**
   * Prépare et envoie une demande d'upload de fichier
   * @param patientId UUID du patient
   * @param file Le fichier à uploader
   * @param doctorKeycloakId ID Keycloak du docteur (pour le backend)
   * @returns Promise<string> UUID de la demande créée
   */
  async uploadFileForPatient(
    patientId: string,
    file: File
  ): Promise<string> {
    try {
      // 1. Récupérer la clé publique RSA du patient
      const patientKeyResp = await this.patientDoctorService
        .getPatientPublicKey(patientId)
        .toPromise();
      if (!patientKeyResp?.publicKeyPEM) {
        throw new Error('Clé publique du patient introuvable');
      }
      const patientPublicKey = await this.crypto.importPublicKey(patientKeyResp.publicKeyPEM);

      // 2. Générer une clé AES temporaire (Ktemp)
      const tempAESKey = await this.crypto.generateAESKey();

      // 3. Lire le fichier et chiffrer son contenu avec Ktemp
      const fileArrayBuffer = await file.arrayBuffer();
      const fileBytes = new Uint8Array(fileArrayBuffer);

      // Générer un IV aléatoire pour AES-GCM
      const iv = new Uint8Array(this.crypto.generateIV(12));
      const ivBase64 = toBase64(iv);

      // Chiffrer le contenu du fichier
      const encryptedContent = await window.crypto.subtle.encrypt(
        { name: 'AES-GCM', iv },
        tempAESKey,
        fileBytes
      );
      const contentEncBase64 = toBase64(encryptedContent as ArrayBuffer);

      // 4. Chiffrer le nom du fichier aussi (optionnel mais recommandé)
      const fileNameBytes = new TextEncoder().encode(file.name);
      const encryptedFileName = await window.crypto.subtle.encrypt(
        { name: 'AES-GCM', iv },
        tempAESKey,
        fileNameBytes
      );
      const fileNameEncBase64 = toBase64(encryptedFileName as ArrayBuffer);

      // 5. Chiffrer le type MIME (optionnel)
      const mimeBytes = new TextEncoder().encode(file.type || 'application/octet-stream');
      const encryptedMime = await window.crypto.subtle.encrypt(
        { name: 'AES-GCM', iv },
        tempAESKey,
        mimeBytes
      );
      const mimeTypeEncBase64 = toBase64(encryptedMime as ArrayBuffer);

      // 6. Envelopper Ktemp avec la clé publique RSA du patient
      const wrappedTempKey = await this.crypto.encryptAESKeyWithRSA(
        tempAESKey,
        patientPublicKey
      );

      // 7. Créer la demande
      const payload: CreatePendingMedicalFileRequest = {
        fileNameEncBase64,
        contentEncBase64,
        ivBase64,
        wrappedTempKeyForPatientBase64: wrappedTempKey,
        mimeTypeEncBase64
      };

      // 8. Envoyer au serveur
      const response = await this.pendingFilesApi
        .createRequest(patientId, payload)
        .toPromise();

      if (!response?.id) {
        throw new Error('Réponse invalide du serveur');
      }

      return response.id;
    } catch (e: unknown) {
      const message = this.extractErrorMessage(e);
      throw new Error(`Erreur lors de la préparation du fichier: ${message}`);
    }
  }

  // Base64 conversions centralized in core/utils/base64.util.ts
}
