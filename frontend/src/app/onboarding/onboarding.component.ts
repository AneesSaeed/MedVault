import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { UserService } from '../core/services/user.service';
import { CryptoService } from '../core/services/crypto.service';
import { KeyStoreService } from '../core/services/key-store.service';
import { LoggingService } from '../core/services/logging.service';

@Component({
  selector: 'app-onboarding',
  templateUrl: './onboarding.component.html',
  styleUrls: ['./onboarding.component.scss']
})
export class OnboardingComponent {

  role: 'PATIENT' | 'DOCTOR' | null = null;
  dateOfBirth = '';
  medicalOrg = '';
  isSubmitting = false;

  constructor(
    public auth: AuthService,
    private userService: UserService,
    private router: Router,
    private cryptoService: CryptoService,
    private keyStore: KeyStoreService,
    private logger: LoggingService,
  ) {}

  selectRole(r: 'PATIENT' | 'DOCTOR') {
    this.role = r;
  }

  async submit() {
    if (!this.role || this.isSubmitting) return;
    this.isSubmitting = true;

    const keycloakId = this.auth.sub;
    this.logger.info(`Starting onboarding for ${this.role}`, { role: this.role }, 'OnboardingComponent');

    try {
      // 1. Génère la paire de clés RSA pour l'utilisateur (TOUS LES RÔLES)
      this.logger.debug('Generating RSA key pair...', { keySize: 2048 }, 'OnboardingComponent');
      const { publicKey, privateKey } = await this.cryptoService.generateRSAKeyPair();
      this.logger.info('RSA key pair generated successfully', {}, 'OnboardingComponent');

      // Store private/public key (CryptoKey) in IndexedDB
      await this.keyStore.putRsaPrivateKey(keycloakId, privateKey);
      await this.keyStore.putRsaPublicKey(keycloakId, publicKey);
      this.logger.debug('Keys stored in IndexedDB', { keycloakId }, 'OnboardingComponent');

      // 2. Exporte la clé publique en PEM pour l'envoyer au serveur
      const publicKeyPEM = await this.cryptoService.exportPublicKey(publicKey);


      if (this.role === 'PATIENT') {
        // PATIENT : créer clé AES, chiffrer données, créer PatientSymmetricKey
        // SÉCURITÉ: Toutes les données personnelles doivent être chiffrées avant envoi au serveur

        this.logger.debug('Generating AES key for patient data...', {}, 'OnboardingComponent');
        // 4. Génère une clé symétrique AES pour les données du patient
        const aesKey = await this.cryptoService.generateAESKey();

        // 5. Chiffre toutes les données personnelles avec la clé AES
        this.logger.debug('Encrypting patient personal data...', { fieldsCount: 4 }, 'OnboardingComponent');
        const [firstNameEnc, lastNameEnc, emailEnc, dobEnc] = await Promise.all([
          this.cryptoService.encryptWithAES(this.auth.firstName, aesKey),
          this.cryptoService.encryptWithAES(this.auth.lastName, aesKey),
          this.cryptoService.encryptWithAES(this.auth.email, aesKey),
          this.cryptoService.encryptWithAES(this.dateOfBirth, aesKey)
        ]);

        // 6. NOUVEAU: Chiffre la clé AES avec la clé publique RSA du patient
        //    (pour que le patient puisse la récupérer depuis la DB sans localStorage)
        const symmetricKeyEnc = await this.cryptoService.encryptAESKeyWithRSA(aesKey, publicKey);
        this.logger.info('Patient data encrypted successfully', { hasAESKey: true }, 'OnboardingComponent');

        // Concatène IV + cipher en un seul buffer Base64 pour le backend
        const concatEncrypted = (iv: string, encrypted: string): string => {
          const ivBuf = this.base64ToArrayBuffer(iv);
          const encBuf = this.base64ToArrayBuffer(encrypted);
          const combined = new Uint8Array(ivBuf.byteLength + encBuf.byteLength);
          combined.set(new Uint8Array(ivBuf), 0);
          combined.set(new Uint8Array(encBuf), ivBuf.byteLength);
          return this.arrayBufferToBase64(combined.buffer);
        };

        const payload = {
          firstNameEncBase64: concatEncrypted(firstNameEnc.iv, firstNameEnc.encrypted),
          lastNameEncBase64: concatEncrypted(lastNameEnc.iv, lastNameEnc.encrypted),
          emailEncBase64: concatEncrypted(emailEnc.iv, emailEnc.encrypted),
          dateOfBirthEncBase64: concatEncrypted(dobEnc.iv, dobEnc.encrypted),
          publicKeyPEM: publicKeyPEM,
          symmetricKeyEncBase64: symmetricKeyEnc
        };

        this.logger.info('Sending patient creation request to backend...', {}, 'OnboardingComponent');
        this.userService.createPatient(payload)
          .subscribe({
            next: () => {
              this.logger.logAction('PATIENT_ONBOARDING_SUCCESS', keycloakId, {
                dateOfBirth: this.dateOfBirth,
                timestamp: Date.now()
              }, 'OnboardingComponent');
              this.router.navigate(['/']);
            },
            error: (err) => {
              this.logger.error('Failed to create patient', err, {
                errorMessage: err?.error?.error || err?.message
              }, 'OnboardingComponent');
              console.error('Erreur lors de la création du patient:', err);
              this.isSubmitting = false;
            }
          });
      }

      if (this.role === 'DOCTOR') {
        // DOCTOR : données en clair (médecins sont découvrables)

        const payload = {
          firstName: this.auth.firstName,
          lastName: this.auth.lastName,
          email: this.auth.email,
          medicalOrganization: this.medicalOrg,
          publicKeyPEM: publicKeyPEM
        };

        this.logger.info('Sending doctor creation request to backend...', {
          organization: this.medicalOrg
        }, 'OnboardingComponent');
        
        this.userService.createDoctor(payload)
          .subscribe({
            next: () => {
              this.logger.logAction('DOCTOR_ONBOARDING_SUCCESS', keycloakId, {
                organization: this.medicalOrg,
                email: this.auth.email,
                timestamp: Date.now()
              }, 'OnboardingComponent');
              this.router.navigate(['/']);
            },
            error: (err) => {
              this.logger.error('Failed to create doctor', err, {
                organization: this.medicalOrg,
                errorMessage: err?.error?.error || err?.message
              }, 'OnboardingComponent');
              console.error('Erreur lors de la création du médecin:', err);
              this.isSubmitting = false;
            }
          });
      }
    } catch (error) {
      this.logger.fatal('Critical error during onboarding', error, {
        role: this.role,
        keycloakId
      }, 'OnboardingComponent');
      console.error('Erreur lors de la génération des clés:', error);
      this.isSubmitting = false;
    }
  }

  // Helpers pour concaténer IV + données chiffrées
  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }

  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  isFormValid(): boolean {
    if (this.role === 'PATIENT') {
      return !!this.dateOfBirth; // must not be empty
    }

    if (this.role === 'DOCTOR') {
      return !!this.medicalOrg && this.medicalOrg.trim().length > 0;
    }

    return false;
  }
}
