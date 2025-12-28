import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService, AppRole } from '../core/services/auth.service';
import { UserService } from '../core/services/user.service';
import { CryptoService } from '../core/services/crypto.service';
import { KeyStoreService } from '../core/services/key-store.service';

@Component({
  selector: 'app-onboarding',
  templateUrl: './onboarding.component.html',
  styleUrls: ['./onboarding.component.scss'],
  standalone: false
})
export class OnboardingComponent implements OnInit {

  role: AppRole | null = null;

  dateOfBirth = '';
  medicalOrg = '';
  isSubmitting = false;

  constructor(
    public auth: AuthService,
    private userService: UserService,
    private router: Router,
    private cryptoService: CryptoService,
    private keyStore: KeyStoreService,
  ) {}

  ngOnInit(): void {
    // Role comes from Keycloak realm roles in the JWT
    this.role = this.auth.userRole;
  }

  async submit() {
    if (!this.role || this.isSubmitting) return;
    this.isSubmitting = true;

    try {
      // 1) Generate RSA keypair for all users
      const { publicKey, privateKey } = await this.cryptoService.generateRSAKeyPair();
      const keycloakId = this.auth.sub;

      // Store private/public key (CryptoKey) in IndexedDB
      await this.keyStore.putRsaPrivateKey(keycloakId, privateKey);
      await this.keyStore.putRsaPublicKey(keycloakId, publicKey);

      // Export public key PEM for backend
      const publicKeyPEM = await this.cryptoService.exportPublicKey(publicKey);

      if (this.role === 'PATIENT') {
        // PATIENT: generate AES key, encrypt all personal data, wrap AES key with RSA
        const aesKey = await this.cryptoService.generateAESKey();

        const [firstNameEnc, lastNameEnc, emailEnc, dobEnc] = await Promise.all([
          this.cryptoService.encryptWithAES(this.auth.firstName ?? '', aesKey),
          this.cryptoService.encryptWithAES(this.auth.lastName ?? '', aesKey),
          this.cryptoService.encryptWithAES(this.auth.email ?? '', aesKey),
          this.cryptoService.encryptWithAES(this.dateOfBirth, aesKey)
        ]);

        const symmetricKeyEnc = await this.cryptoService.encryptAESKeyWithRSA(aesKey, publicKey);

        // Concat IV + cipher into one Base64 blob for backend
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
            console.error('Erreur lors de la création du patient:', err);
            this.isSubmitting = false;
          }
        });

        return;
      }

      // DOCTOR: cleartext profile (discoverable)
      const payload = {
        firstName: this.auth.firstName ?? '',
        lastName: this.auth.lastName ?? '',
        email: this.auth.email ?? '',
        medicalOrganization: this.medicalOrg,
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
          console.error('Erreur lors de la création du médecin:', err);
          this.isSubmitting = false;
        }
      });

    } catch (error) {
      console.error('Erreur lors de la génération des clés:', error);
      this.isSubmitting = false;
    }
  }

  isFormValid(): boolean {
    if (!this.role) return false;

    if (this.role === 'PATIENT') {
      return !!this.dateOfBirth;
    }

    // DOCTOR
    return !!this.medicalOrg && this.medicalOrg.trim().length > 0;
  }

  // Helpers for IV+encrypted concatenation
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
}
