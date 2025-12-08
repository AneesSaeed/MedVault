import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { UserService } from '../core/services/user.service';
import { CryptoService } from '../core/services/crypto.service';

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
    private cryptoService: CryptoService
  ) {}

  selectRole(r: 'PATIENT' | 'DOCTOR') {
    this.role = r;
  }

  async submit() {
    if (!this.role || this.isSubmitting) return;
    this.isSubmitting = true;

    try {
      // 1. Génère la paire de clés RSA pour l'utilisateur
      const { publicKey, privateKey } = await this.cryptoService.generateRSAKeyPair();
      
      // 2. Exporte la clé publique en PEM pour l'envoyer au serveur
      const publicKeyPEM = await this.cryptoService.exportPublicKey(publicKey);
      
      // 3. Exporte la clé privée en PEM et la stocke localement (jamais envoyée au serveur)
      const privateKeyPEM = await this.cryptoService.exportPrivateKey(privateKey);
      const keycloakId = this.auth.sub;
      this.cryptoService.storePrivateKey(keycloakId, privateKeyPEM);
      // Stocke aussi la clé publique pour usage futur (chiffrement des données pour le patient)
      this.cryptoService.storePublicKey(keycloakId, publicKeyPEM);

      if (this.role === 'PATIENT') {
        // PATIENT : Nom/prénom en clair, dateOfBirth et email chiffrés
        
        // 4. Génère une clé symétrique AES pour chiffrer les données sensibles du patient
        const aesKey = await this.cryptoService.generateAESKey();
        
        // 5. Stocke la clé AES localement (pour usage futur côté client)
        const aesKeyBase64 = await this.cryptoService.exportAESKey(aesKey);
        this.cryptoService.storeAESKey(keycloakId, aesKeyBase64);

        // 6. Chiffre les données sensibles avec la clé AES (dateOfBirth, email)
        const emailEnc = await this.cryptoService.encryptWithAES(this.auth.email, aesKey);
        const dobEnc = await this.cryptoService.encryptWithAES(this.dateOfBirth, aesKey);

        // Concatène l'IV avec les données chiffrées (IV + données) pour simplifier le stockage
        const concatEncrypted = (iv: string, encrypted: string): string => {
          const ivBuf = this.base64ToArrayBuffer(iv);
          const encBuf = this.base64ToArrayBuffer(encrypted);
          const combined = new Uint8Array(ivBuf.byteLength + encBuf.byteLength);
          combined.set(new Uint8Array(ivBuf), 0);
          combined.set(new Uint8Array(encBuf), ivBuf.byteLength);
          return this.arrayBufferToBase64(combined.buffer);
        };

        // User payload : firstName et lastName en clair, email chiffré
        const userPayload = {
          keycloakId: keycloakId,
          firstName: this.auth.firstName, // EN CLAIR
          lastName: this.auth.lastName,   // EN CLAIR
          emailEnc: concatEncrypted(emailEnc.iv, emailEnc.encrypted),
          role: this.role
        };
        
        const payload = {
          user: userPayload,
          dateOfBirthEncBase64: concatEncrypted(dobEnc.iv, dobEnc.encrypted),
          publicKeyPEM: publicKeyPEM
        };

        this.userService.createPatient(payload)
          .subscribe({
            next: () => this.router.navigate(['/']),
            error: (err) => {
              console.error('Erreur lors de la création du patient:', err);
              this.isSubmitting = false;
            }
          });
      }

      if (this.role === 'DOCTOR') {
        // DOCTOR : Tout en clair (nom, prénom, organisation)
        
        // User payload : tout en clair, pas de chiffrement nécessaire
        const userPayload = {
          keycloakId: keycloakId,
          firstName: this.auth.firstName, // EN CLAIR
          lastName: this.auth.lastName,   // EN CLAIR
          role: this.role
        };
        
        const payload = {
          user: userPayload,
          medicalOrganization: this.medicalOrg, // EN CLAIR
          publicKeyPEM: publicKeyPEM
        };

        this.userService.createDoctor(payload)
          .subscribe({
            next: () => this.router.navigate(['/']),
            error: (err) => {
              console.error('Erreur lors de la création du médecin:', err);
              this.isSubmitting = false;
            }
          });
      }
    } catch (error) {
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
