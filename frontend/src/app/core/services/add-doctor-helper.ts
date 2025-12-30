import { CryptoService } from './crypto.service';
import { KeyStoreService } from './key-store.service';
import { PatientDoctorService } from './patient-doctor.service';
import { MedicalFilesApi } from '../api/medical-files.api';
import { PatientDataApi } from '../api/patient-data.api';
import { Injectable, inject } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AddDoctorHelper {
  private readonly crypto = inject(CryptoService);
  private readonly patientDoctorService = inject(PatientDoctorService);
  private readonly medicalFilesApi = inject(MedicalFilesApi);
  private readonly patientDataApi = inject(PatientDataApi);
  private readonly keyStore = inject(KeyStoreService);

  async addDoctorToPatient(
    doctorId: string,
    patientUserId: string,
    patientKeycloakId: string
  ): Promise<void> {

    const doctorPublicKeyResponse = await this.patientDoctorService.getDoctorPublicKey(doctorId).toPromise();
    const doctorPublicKeyPEM = doctorPublicKeyResponse?.publicKeyPEM;
    if (!doctorPublicKeyPEM) throw new Error('Failed to retrieve doctor public key');
    const doctorPublicKey = await this.crypto.importPublicKey(doctorPublicKeyPEM);


    const patientPrivKey = await this.keyStore.getRsaPrivateKey(patientKeycloakId);
    if (!patientPrivKey) {
      throw new Error('Patient private RSA key not found in IndexedDB (this device)');
    }

    // Récupère la clé AES chiffrée depuis la BD
    const patientDataRes = await this.patientDataApi.getPatientData(patientUserId).toPromise();
    if (!patientDataRes?.symmetricKeyEncBase64) {
      throw new Error('Patient symmetric key not found in database');
    }

    // Déchiffre la clé AES du patient avec sa clé privée RSA
    const patientAESKey = await this.crypto.decryptAESKeyWithRSA(
      patientDataRes.symmetricKeyEncBase64,
      patientPrivKey
    );

    // Chiffre la clé AES du patient avec la clé publique du docteur
    const encryptedPatientAESKey = await this.crypto.encryptAESKeyWithRSA(patientAESKey, doctorPublicKey);

    await this.patientDoctorService.addDoctorToPatient({
      doctorId,
      encryptedPatientAESKeyBase64: encryptedPatientAESKey
    }).toPromise();


    const filesRes = await this.medicalFilesApi.list().toPromise();
    const files = filesRes?.files ?? [];

    const items: { fileId: string; wrappedKeyForDoctorBase64: string }[] = [];

    for (const f of files) {
      // unwrap K_file using patient private RSA
      const fileKey = await this.crypto.decryptAESKeyWithRSA(f.wrappedFileKeyEncBase64, patientPrivKey);

      // wrap K_file for doctor
      const wrappedForDoctor = await this.crypto.encryptAESKeyWithRSA(fileKey, doctorPublicKey);

      items.push({ fileId: f.id, wrappedKeyForDoctorBase64: wrappedForDoctor });
    }

    if (items.length > 0) {
      await this.medicalFilesApi.shareKeysWithDoctor(doctorId, items).toPromise();
    }
  }
}
