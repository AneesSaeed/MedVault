import { Component, OnInit } from '@angular/core';
import { PatientDoctorService } from '../core/services/patient-doctor.service';
import { CryptoService } from '../core/services/crypto.service';
import { AuthService } from '../core/services/auth.service';

type RawPatient = {
  patientId: string;
  firstNameEnc: string; // base64(IV(12)+cipher)
  lastNameEnc: string;  // base64(IV(12)+cipher)
  emailEnc: string;     // base64(IV(12)+cipher)
  encryptedAESKey: string; // base64 (RSA-OAEP encrypted AES key)
};

type DecryptedPatient = {
  patientId: string;
  firstName: string;
  lastName: string;
  email: string;
  dob?: string;
};

@Component({
  selector: 'app-my-patients',
  templateUrl: './my-patients.component.html',
  styleUrls: ['./my-patients.component.scss']
})
export class MyPatientsComponent implements OnInit {
  loading = false;
  error: string | null = null;
  detailsLoading: Record<string, boolean> = {};

  patients: DecryptedPatient[] = [];

  constructor(
    private service: PatientDoctorService,
    private crypto: CryptoService,
    private auth: AuthService
  ) {}

  async ngOnInit() {
    await this.loadAndDecryptPatients();
  }

  private async loadAndDecryptPatients() {
    this.loading = true;
    this.error = null;
    try {
      const res = await this.service.getMyPatients().toPromise();
      const list: RawPatient[] = (res?.patients ?? []) as RawPatient[];

      const privatePem = this.crypto.getPrivateKey(this.auth.sub);
      if (!privatePem) {
        throw new Error("Clé privée introuvable dans le navigateur (connexion requise sur l'appareil qui a généré la clé)");
      }
      const privateKey = await this.crypto.importPrivateKey(privatePem);

      const decrypted: DecryptedPatient[] = [];
      for (const item of list) {
        // 1) Déchiffrer la clé AES du patient avec la clé privée du médecin
        const aesKey = await this.crypto.decryptAESKeyWithRSA(item.encryptedAESKey, privateKey);

        // 2) Déchiffrer les champs individuels (IV(12) + ciphertext)
        const firstName = await this.decryptCombinedAESField(item.firstNameEnc, aesKey);
        const lastName = await this.decryptCombinedAESField(item.lastNameEnc, aesKey);
        const email = await this.decryptCombinedAESField(item.emailEnc, aesKey);

        decrypted.push({
          patientId: item.patientId,
          firstName,
          lastName,
          email
        });
      }

      this.patients = decrypted;
    } catch (e: any) {
      console.error(e);
      this.error = e?.message || 'Impossible de charger les patients';
    } finally {
      this.loading = false;
    }
  }

  async loadDetails(p: DecryptedPatient) {
    if (this.detailsLoading[p.patientId]) return;
    this.detailsLoading[p.patientId] = true;
    try {
      const res = await this.service.getPatientData(p.patientId).toPromise();
      const dateOfBirthEnc = res?.dateOfBirthEnc as string;
      const encryptedAESKey = res?.encryptedAESKey as string;

      // Re-déduire la clé AES à partir des détails (sécurité: peut différer?)
      const privatePem = this.crypto.getPrivateKey(this.auth.sub);
      if (!privatePem) throw new Error('Clé privée introuvable');
      const privateKey = await this.crypto.importPrivateKey(privatePem);
      const aesKey = await this.crypto.decryptAESKeyWithRSA(encryptedAESKey, privateKey);

      const dob = await this.decryptCombinedAESField(dateOfBirthEnc, aesKey);
      p.dob = dob;
    } catch (e) {
      console.error(e);
    } finally {
      this.detailsLoading[p.patientId] = false;
    }
  }

  private async decryptCombinedAESField(combinedBase64: string, key: CryptoKey): Promise<string> {
    const combined = this.base64ToUint8Array(combinedBase64);
    const iv = combined.slice(0, 12);
    const cipher = combined.slice(12);
    const ivB64 = btoa(String.fromCharCode(...iv));
    const cipherB64 = btoa(String.fromCharCode(...cipher));
    return await this.crypto.decryptWithAES(cipherB64, ivB64, key);
  }

  private base64ToUint8Array(base64: string): Uint8Array {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }
}
