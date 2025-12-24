import { Injectable } from '@angular/core';
import { CryptoService } from './crypto.service';
import { KeyStoreService } from './key-store.service';
import { PatientDataApi, PatientDataModel } from '../api/patient-data.api';
import { PatientData } from '../models/patient-data.model';

/**
 * Service pour gérer les données d'un patient.
 *
 * Combine:
 * - PatientDataApi (récupère données chiffrées depuis backend)
 * - CryptoService (déchiffre avec clé privée RSA)
 * - Expose PatientData déchiffrées au frontend
 */
@Injectable({ providedIn: 'root' })
export class PatientDataService {
  constructor(
    private patientDataApi: PatientDataApi,
    private crypto: CryptoService,
    private keyStore: KeyStoreService
  ) {}

  /**
   * Récupère et déchiffre les données d'un patient.
   *
   * Process:
   * 1. Appel backend pour obtenir données chiffrées + clé symétrique chiffrée
   * 2. Déchiffre clé symétrique avec clé privée RSA
   * 3. Déchiffre données personnelles avec clé symétrique
   * 4. Retourne PatientData déchiffrées
   *
   * @param patientId ID UUID du patient
   * @param keycloakId ID Keycloak de l'utilisateur (pour récupérer clé privée RSA)
   * @returns PatientData déchiffrées
   */
  async getPatientData(patientId: string, keycloakId: string): Promise<PatientData> {
    // 1. Récupérer données chiffrées du backend
    const encryptedData = await this.patientDataApi.getPatientData(patientId).toPromise();
    if (!encryptedData) {
      throw new Error('Failed to fetch patient data from server');
    }

    // NEW:
    const privKey = await this.keyStore.getRsaPrivateKey(keycloakId);
    if (!privKey) {
      throw new Error('Patient private RSA key not found in IndexedDB (this device)');
    }

    // 3. Déchiffrer clé symétrique avec clé privée RSA
    const symmetricKey = await this.crypto.decryptAESKeyWithRSA(
      encryptedData.symmetricKeyEncBase64,
      privKey
    );

    // 4. Déchiffrer données personnelles avec clé symétrique
    const [firstName, lastName, email, dateOfBirth] = await Promise.all([
      this.decryptField(encryptedData.firstNameEncBase64, symmetricKey),
      this.decryptField(encryptedData.lastNameEncBase64, symmetricKey),
      this.decryptField(encryptedData.emailEncBase64, symmetricKey),
      this.decryptField(encryptedData.dateOfBirthEncBase64, symmetricKey)
    ]);

    return {
      patientId: encryptedData.patientId,
      firstName,
      lastName,
      email,
      dateOfBirth
    };
  }

  /**
   * Déchiffre un champ de données avec une clé AES symétrique.
   *
   * @param encBase64 Données chiffrées (IV + ciphertext concaténés), encodées en Base64
   * @param aesKey Clé AES symétrique importée
   * @returns Plaintext déchiffré
   */
  private async decryptField(encBase64: string, aesKey: CryptoKey): Promise<string> {
    // Décoder de Base64
    const combined = this.base64ToArrayBuffer(encBase64);
    const combined8 = new Uint8Array(combined);

    // Extraire IV (premiers 12 bytes pour GCM) et ciphertext
    const IV_SIZE = 12;
    const iv = combined8.slice(0, IV_SIZE);
    const ciphertext = combined8.slice(IV_SIZE);

    // Déchiffrer avec AES-GCM
    const decrypted = await window.crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: iv },
      aesKey,
      ciphertext
    );

    // Convertir en string UTF-8
    return new TextDecoder().decode(decrypted);
  }

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }
}
