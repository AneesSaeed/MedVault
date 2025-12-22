import { CryptoService } from './crypto.service';
import { PatientDoctorService } from './patient-doctor.service';
import { MedicalFilesApi } from '../api/medical-files.api';

export class AddDoctorHelper {
  constructor(
    private crypto: CryptoService,
    private patientDoctorService: PatientDoctorService,
    private medicalFilesApi: MedicalFilesApi
  ) {}

  async addDoctorToPatient(doctorId: string, patientKeycloakId: string): Promise<void> {
    // 1) doctor public key
    const doctorPublicKeyResponse = await this.patientDoctorService.getDoctorPublicKey(doctorId).toPromise();
    const doctorPublicKeyPEM = doctorPublicKeyResponse?.publicKeyPEM;
    if (!doctorPublicKeyPEM) throw new Error('Failed to retrieve doctor public key');
    const doctorPublicKey = await this.crypto.importPublicKey(doctorPublicKeyPEM);

    // 2) keep your existing link-encrypted AES if your patients list page still uses it
    //    (optional: you can keep it for decrypting patient identity fields)
    const patientAESKeyBase64 = this.crypto.getAESKey(patientKeycloakId);
    if (!patientAESKeyBase64) throw new Error('Patient AES key not found in localStorage');
    const patientAESKey = await this.crypto.importAESKey(patientAESKeyBase64);

    const encryptedPatientAESKey = await this.crypto.encryptAESKeyWithRSA(patientAESKey, doctorPublicKey);

    await this.patientDoctorService.addDoctorToPatient({
      doctorId,
      encryptedPatientAESKeyBase64: encryptedPatientAESKey
    }).toPromise();

    // 3) NEW: share wrapped per-file keys for all existing files
    const patientPrivPem = this.crypto.getPrivateKey(patientKeycloakId);
    if (!patientPrivPem) throw new Error('Patient private key not found');
    const patientPrivKey = await this.crypto.importPrivateKey(patientPrivPem);

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
