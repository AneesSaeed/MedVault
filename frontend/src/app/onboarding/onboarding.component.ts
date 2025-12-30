import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService, AppRole } from '../core/services/auth.service';
import { UserService } from '../core/services/user.service';
import { CryptoService } from '../core/services/crypto.service';
import { KeyStoreService } from '../core/services/key-store.service';
import { LoggingService } from '../core/services/logging.service';

@Component({
  selector: 'app-onboarding',
  templateUrl: './onboarding.component.html',
  styleUrls: ['./onboarding.component.scss'],
  standalone: false
})
export class OnboardingComponent implements OnInit {

  role: AppRole | null = null;

  // collected in onboarding (DB-owned)
  firstName = '';
  lastName = '';

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

  ngOnInit(): void {
    this.role = this.auth.userRole;
  }

  async submit() {
    if (!this.role || this.isSubmitting) return;
    if (!this.isFormValid()) return;

    const emailFromToken = (this.auth.email ?? '').trim();
    if (!emailFromToken) {
      // email is required from JWT per your design
      this.logger.error('Missing email in token', null, {}, 'OnboardingComponent');
      return;
    }

    this.isSubmitting = true;

    const keycloakId = this.auth.sub;
    const firstName = this.firstName.trim();
    const lastName = this.lastName.trim();

    this.logger.info(`Starting onboarding for ${this.role}`, { role: this.role }, 'OnboardingComponent');

    try {
      // 1) Generate RSA keypair
      const { publicKey, privateKey } = await this.cryptoService.generateRSAKeyPair();

      await this.keyStore.putRsaPrivateKey(keycloakId, privateKey);
      await this.keyStore.putRsaPublicKey(keycloakId, publicKey);

      const publicKeyPEM = await this.cryptoService.exportPublicKey(publicKey);

      if (this.role === 'PATIENT') {
        const aesKey = await this.cryptoService.generateAESKey();

        const [firstNameEnc, lastNameEnc, emailEnc, dobEnc] = await Promise.all([
          this.cryptoService.encryptWithAES(firstName, aesKey),
          this.cryptoService.encryptWithAES(lastName, aesKey),
          this.cryptoService.encryptWithAES(emailFromToken, aesKey),
          this.cryptoService.encryptWithAES(this.dateOfBirth, aesKey)
        ]);

        const symmetricKeyEnc = await this.cryptoService.encryptAESKeyWithRSA(aesKey, publicKey);

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
          publicKeyPEM,
          symmetricKeyEncBase64: symmetricKeyEnc
        };

        this.userService.createPatient(payload).subscribe({
          next: async () => {
            try {
              await this.auth.refreshToken();
            } finally {
              this.router.navigate(['/']);
            }
          },
          error: (err) => {
            this.logger.error('Failed to create patient', err, {
              errorMessage: err?.error?.error || err?.message
            }, 'OnboardingComponent');
            this.isSubmitting = false;
          }
        });

        return;
      }

      // DOCTOR: cleartext profile (discoverable)
      const payload = {
        firstName,
        lastName,
        email: emailFromToken,
        medicalOrganization: this.medicalOrg.trim(),
        publicKeyPEM
      };

      this.userService.createDoctor(payload).subscribe({
        next: async () => {
          try {
            await this.auth.refreshToken();
          } finally {
            this.router.navigate(['/']);
          }
        },
        error: (err) => {
          this.logger.error('Failed to create doctor', err, {
            organization: this.medicalOrg,
            errorMessage: err?.error?.error || err?.message
          }, 'OnboardingComponent');
          this.isSubmitting = false;
        }
      });

    } catch (error) {
      this.logger.fatal('Critical error during onboarding', error, {
        role: this.role,
        keycloakId
      }, 'OnboardingComponent');
      this.isSubmitting = false;
    }
  }

  isFormValid(): boolean {
    if (!this.role) return false;

    const firstNameOk = this.firstName.trim().length > 0;
    const lastNameOk = this.lastName.trim().length > 0;
    const emailOk = !!(this.auth.email && this.auth.email.trim().length > 0);
    if (!firstNameOk || !lastNameOk || !emailOk) return false;

    if (this.role === 'PATIENT') return !!this.dateOfBirth;
    return this.medicalOrg.trim().length > 0;
  }

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
  }

  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
    return btoa(binary);
  }
}
