import { CryptoService } from './crypto.service';
import { PatientDoctorService } from './patient-doctor.service';

/**
 * Helper pour ajouter un médecin à un patient.
 * 
 * Selon l'énoncé : "A patient can add or remove a doctor to his list of appointed doctors."
 * 
 * Flux simplifié :
 * 1. Patient récupère la clé publique RSA du médecin
 * 2. Patient chiffre sa clé AES avec la clé publique RSA du médecin
 * 3. Patient envoie la clé AES chiffrée au serveur
 * 
 * Note: Les informations du médecin (nom, prénom, organisation) sont en clair,
 * donc pas besoin de les chiffrer.
 */
export class AddDoctorHelper {
  constructor(
    private cryptoService: CryptoService,
    private patientDoctorService: PatientDoctorService
  ) {}

  /**
   * Ajoute un médecin à la liste des médecins du patient.
   * 
   * @param doctorId ID du médecin à ajouter
   * @param patientKeycloakId Keycloak ID du patient (pour récupérer les clés)
   * @returns Promise qui se résout quand le médecin est ajouté
   */
  async addDoctorToPatient(
    doctorId: string,
    patientKeycloakId: string
  ): Promise<void> {
    // 1. Récupère la clé publique RSA du médecin
    const doctorPublicKeyResponse = await this.patientDoctorService.getDoctorPublicKey(doctorId).toPromise();
    const doctorPublicKeyPEM = doctorPublicKeyResponse?.publicKeyPEM;
    if (!doctorPublicKeyPEM) {
      throw new Error('Failed to retrieve doctor public key');
    }

    // 2. Importe la clé publique RSA du médecin
    const doctorPublicKey = await this.cryptoService.importPublicKey(doctorPublicKeyPEM);

    // 3. Récupère la clé AES du patient depuis le localStorage
    const patientAESKeyBase64 = this.cryptoService.getAESKey(patientKeycloakId);
    if (!patientAESKeyBase64) {
      throw new Error('Patient AES key not found in localStorage');
    }
    const patientAESKey = await this.cryptoService.importAESKey(patientAESKeyBase64);

    // 4. Chiffre la clé AES du patient avec la clé publique RSA du médecin
    const encryptedPatientAESKey = await this.cryptoService.encryptAESKeyWithRSA(
      patientAESKey,
      doctorPublicKey
    );
    
    // 5. Envoie la clé AES chiffrée au serveur
    await this.patientDoctorService.addDoctorToPatient({
      doctorId: doctorId,
      encryptedPatientAESKeyBase64: encryptedPatientAESKey
    }).toPromise();
  }
}

