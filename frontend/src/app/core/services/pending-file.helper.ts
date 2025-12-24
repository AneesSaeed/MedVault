import { Injectable, inject } from '@angular/core';
import { CryptoService } from './crypto.service';
import { KeyStoreService } from './key-store.service';
import { PendingMedicalFilesApi } from '../api/pending-medical-files.api';
import { MedicalFilesApi } from '../api/medical-files.api';
import { PatientDoctorService } from './patient-doctor.service';
import { fromBase64 } from '../utils/base64.util';

/**
 * Interface pour afficher une demande de fichier déchiffrée à l'écran du patient
 */
export interface DecryptedPendingFile {
  id: string;
  uploaderDoctorId: string;
  fileName: string;
  mimeType: string;
  fileSize: number;
  createdAt: string;
  // Données chiffrées gardées pour déchiffrement final lors de l'acceptation
  _encryptedContent: string; // contentEncBase64
  _iv: string;               // ivBase64
  _wrappedTempKey: string;   // wrappedTempKeyForPatientBase64
}

/**
 * Service helper pour gérer les demandes d'upload en attente côté patient
 *
 * Flux:
 * 1. Patient reçoit une demande (notification)
 * 2. Patient voit la liste des demandes en attente (partiellement déchiffrées)
 * 3. Patient accepte/rejette chaque demande
 * 4. Si acceptation: le fichier est déchiffré avec la clé privée RSA du patient
 *    et devient un dossier médical permanent
 */
@Injectable({
  providedIn: 'root'
})
export class PendingFileHelper {
  private readonly crypto = inject(CryptoService);
  private readonly keyStore = inject(KeyStoreService);
  private readonly pendingFilesApi = inject(PendingMedicalFilesApi);
  private readonly medicalFilesApi = inject(MedicalFilesApi);
  private readonly patientDoctorService = inject(PatientDoctorService);

  private extractErrorMessage(error: unknown): string {
    if (error && typeof error === 'object') {
      const errObj = error as {
        error?: { error?: unknown; message?: unknown };
        message?: unknown;
        status?: unknown;
        statusText?: unknown;
      };
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
   * Récupère et déchiffre les demandes en attente pour le patient actuel
   * Les données sensibles (nom du fichier, etc.) sont chiffrées avec Ktemp
   * qui lui-même est chiffré avec la clé publique RSA du patient
   *
   * @param keycloakId ID Keycloak du patient (pour récupérer la clé privée RSA)
   * @returns Promise<DecryptedPendingFile[]>
   */
  async listPendingRequests(keycloakId: string): Promise<DecryptedPendingFile[]> {
    try {
      // 1. Récupérer la liste des demandes (données restent chiffrées)
      const requests = await this.pendingFilesApi.listPendingRequests().toPromise();
      if (!requests) return [];

      // 2. Récupérer la clé privée RSA du patient
      const patientPrivateKey = await this.keyStore.getRsaPrivateKey(keycloakId);
      if (!patientPrivateKey) {
        throw new Error('Clé privée RSA du patient introuvable');
      }

      // 3. Pour chaque demande, déchiffrer les métadonnées
      const decrypted: DecryptedPendingFile[] = [];
      for (const req of requests) {
        try {
          // Déchiffrer Ktemp avec la clé privée RSA
          const tempAESKey = await this.crypto.decryptAESKeyWithRSA(
            req.wrappedTempKeyForPatientBase64,
            patientPrivateKey
          );

          // Récupérer l'IV depuis la demande
          const iv = new Uint8Array(fromBase64(req.ivBase64));

          // Déchiffrer le nom du fichier
          let fileName = 'Fichier sans nom';
          if (req.fileNameEncBase64) {
            try {
              const encryptedFileName = new Uint8Array(fromBase64(req.fileNameEncBase64));
              const decryptedFileName = await window.crypto.subtle.decrypt(
                { name: 'AES-GCM', iv },
                tempAESKey,
                encryptedFileName
              );
              fileName = new TextDecoder().decode(decryptedFileName);
            } catch (e) {
              console.error('Erreur déchiffrement du nom de fichier:', e);
            }
          }

          // Déchiffrer le type MIME
          let mimeType = 'application/octet-stream';
          if (req.mimeTypeEncBase64) {
            try {
              const encryptedMime = new Uint8Array(fromBase64(req.mimeTypeEncBase64));
              const decryptedMime = await window.crypto.subtle.decrypt(
                { name: 'AES-GCM', iv },
                tempAESKey,
                encryptedMime
              );
              mimeType = new TextDecoder().decode(decryptedMime);
            } catch (e) {
              console.error('Erreur déchiffrement du MIME type:', e);
            }
          }

          // Estimer la taille du fichier (approximation)
          const encryptedContent = new Uint8Array(fromBase64(req.contentEncBase64));
          const fileSize = encryptedContent.length; // Approximatif (contient IV + cipher + tag)

          decrypted.push({
            id: req.id,
            uploaderDoctorId: req.uploaderDoctorId,
            fileName,
            mimeType,
            fileSize,
            createdAt: req.createdAt,
            _encryptedContent: req.contentEncBase64,
            _iv: req.ivBase64,
            _wrappedTempKey: req.wrappedTempKeyForPatientBase64
          });
        } catch (e) {
          console.error(`Erreur déchiffrement de la demande ${req.id}:`, e);
          // Continuer avec la prochaine demande
        }
      }

      return decrypted;
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e);
      throw new Error(`Erreur lors de la récupération des demandes: ${message}`);
    }
  }

  /**
   * Rejette (supprime) une demande d'upload
   * @param requestId UUID de la demande
   */
  async rejectRequest(requestId: string): Promise<void> {
    try {
      await this.pendingFilesApi.rejectRequest(requestId).toPromise();
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e);
      throw new Error(`Erreur lors du rejet de la demande: ${message}`);
    }
  }

  /**
   * Accepte une demande et déchiffre le contenu du fichier
   * Le patient peut ensuite décider de sauvegarder/partager le fichier
   *
   * @param keycloakId ID Keycloak du patient (pour récupérer la clé privée RSA)
   * @param pendingFile Demande déchiffrement (depuis listPendingRequests)
   * @returns Promise<Blob> Le contenu du fichier déchiffré
   */
  async viewFile(
    keycloakId: string,
    pendingFile: DecryptedPendingFile
  ): Promise<Blob> {
    try {
      // 1. Récupérer la clé privée RSA du patient
      const patientPrivateKey = await this.keyStore.getRsaPrivateKey(keycloakId);
      if (!patientPrivateKey) {
        throw new Error('Clé privée RSA du patient introuvable');
      }

      // 2. Déchiffrer Ktemp
      const tempAESKey = await this.crypto.decryptAESKeyWithRSA(
        pendingFile._wrappedTempKey,
        patientPrivateKey
      );

      // 3. Récupérer l'IV et le contenu chiffré
      const iv = new Uint8Array(fromBase64(pendingFile._iv));
      const encryptedContent = new Uint8Array(fromBase64(pendingFile._encryptedContent));

      // 4. Déchiffrer le contenu
      const decryptedContent = await window.crypto.subtle.decrypt(
        { name: 'AES-GCM', iv },
        tempAESKey,
        encryptedContent
      );

      // 5. Créer un Blob avec le contenu déchiffré
      return new Blob([decryptedContent], { type: pendingFile.mimeType });
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e);
      throw new Error(`Erreur lors du déchiffrement du fichier: ${message}`);
    }
  }

  /**
   * Accepte une demande, ajoute le fichier au dossier médical et le partage avec tous les médecins
   * 
   * @param keycloakId ID Keycloak du patient (pour récupérer la clé privée RSA)
   * @param pendingFile Demande à accepter (depuis listPendingRequests)
   * @returns Promise<string> ID du fichier médical créé
   */
  async acceptAndAddToMedicalRecord(
    keycloakId: string,
    pendingFile: DecryptedPendingFile
  ): Promise<string> {
    try {
      // 1. Récupérer la clé privée RSA du patient
      const patientPrivateKey = await this.keyStore.getRsaPrivateKey(keycloakId);
      if (!patientPrivateKey) {
        throw new Error('Clé privée RSA du patient introuvable');
      }

      // 2. Déchiffrer Ktemp
      const tempAESKey = await this.crypto.decryptAESKeyWithRSA(
        pendingFile._wrappedTempKey,
        patientPrivateKey
      );

      // 3. Récupérer l'IV et le contenu chiffré
      const iv = new Uint8Array(fromBase64(pendingFile._iv));
      const encryptedContent = new Uint8Array(fromBase64(pendingFile._encryptedContent));

      // 4. Déchiffrer le contenu
      const decryptedContent = await window.crypto.subtle.decrypt(
        { name: 'AES-GCM', iv },
        tempAESKey,
        encryptedContent
      );

      // 5. Générer une nouvelle clé AES pour le fichier médical (K_file)
      const fileKey = await this.crypto.generateAESKey();

      // 6. Chiffrer les métadonnées avec K_file
      const nameEnc = await this.crypto.encryptWithAES(pendingFile.fileName, fileKey);
      const dateEnc = await this.crypto.encryptWithAES(new Date().toISOString(), fileKey);

      const fileNameEncBase64 = this.crypto.packIvCipherToBase64(nameEnc.iv, nameEnc.encrypted);
      const uploadDateEncBase64 = this.crypto.packIvCipherToBase64(dateEnc.iv, dateEnc.encrypted);

      // 7. Rechiffrer le contenu avec K_file
      const encFile = await this.crypto.encryptBytesWithAES(decryptedContent, fileKey);
      const packedContent = this.crypto.packIvAndCiphertext(encFile.iv, encFile.encrypted);
      const blob = new Blob([new Uint8Array(packedContent)], { type: pendingFile.mimeType || 'application/octet-stream' });

      // 8. Wrapper K_file avec la clé publique RSA du patient
      const patientPublicKey = await this.keyStore.getRsaPublicKey(keycloakId);
      if (!patientPublicKey) {
        throw new Error('Clé publique RSA du patient introuvable');
      }
      const wrappedFileKeyForPatient = await this.crypto.encryptAESKeyWithRSA(fileKey, patientPublicKey);

      // 9. Créer le FormData pour l'upload (structure attendue par le backend)
      const formData = new FormData();
      formData.append('fileNameEncBase64', fileNameEncBase64);
      formData.append('uploadDateEncBase64', uploadDateEncBase64);
      formData.append('wrappedKeyForPatientBase64', wrappedFileKeyForPatient);
      formData.append('file', blob, 'content.enc');

      // 10. Uploader le fichier médical
      const uploadRes = await this.medicalFilesApi.upload(formData).toPromise();
      if (!uploadRes?.fileId) {
        throw new Error('Échec de l\'upload du fichier médical');
      }

      // 11. Récupérer la liste des médecins du patient
      const myDoctorsRes = await this.patientDoctorService.getMyDoctors().toPromise();
      const doctorIds: string[] = myDoctorsRes?.doctorIds ?? [];

      // 12. Partager K_file avec chaque médecin
      for (const doctorId of doctorIds) {
        try {
          // Récupérer la clé publique du médecin
          const doctorPubKeyRes = await this.patientDoctorService.getDoctorPublicKey(doctorId).toPromise();
          const doctorPublicKeyPEM = doctorPubKeyRes?.publicKeyPEM;
          if (!doctorPublicKeyPEM) {
            console.error(`Clé publique introuvable pour le médecin ${doctorId}`);
            continue;
          }
          const doctorPublicKey = await this.crypto.importPublicKey(doctorPublicKeyPEM);

          // Wrapper K_file avec la clé publique du médecin
          const wrappedForDoctor = await this.crypto.encryptAESKeyWithRSA(fileKey, doctorPublicKey);

          // Partager avec le médecin
          await this.medicalFilesApi.shareKeysWithDoctor(doctorId, [
            { fileId: uploadRes.fileId, wrappedKeyForDoctorBase64: wrappedForDoctor }
          ]).toPromise();
        } catch (e) {
          console.error(`Erreur partage avec médecin ${doctorId}:`, e);
          // Continuer avec les autres médecins
        }
      }

      // 13. Supprimer la demande en attente
      await this.rejectRequest(pendingFile.id);

      return uploadRes.fileId;
    } catch (e: unknown) {
      const message = this.extractErrorMessage(e);
      throw new Error(`Erreur lors de l'acceptation du fichier: ${message}`);
    }
  }
}
