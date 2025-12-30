import { Injectable, inject } from '@angular/core';
import { Observable, from, switchMap, map } from 'rxjs';
import { UserService, MeResponse } from './user.service';
import { AuthService, AppRole } from './auth.service';
import { CryptoService } from './crypto.service';
import { KeyStoreService } from './key-store.service';
import { LoggingService } from './logging.service';

@Injectable({ providedIn: 'root' })
export class UserContextService {
  private readonly userService = inject(UserService);
  private readonly auth = inject(AuthService);
  private readonly crypto = inject(CryptoService);
  private readonly keyStore = inject(KeyStoreService);
  private readonly logger = inject(LoggingService);

  get role(): AppRole | null {
    return this.auth.userRole;
  }

  userId: string | null = null;

  firstName: string | null = null;
  lastName: string | null = null;

  loadUserContext$(): Observable<MeResponse> {
    return this.userService.getMe().pipe(
      switchMap((data) => {
        this.userId = data?.userId ?? null;

        if (this.role === 'DOCTOR') {
          this.firstName = data.firstName ?? null;
          this.lastName = data.lastName ?? null;
          return from([data]);
        }

        if (this.role === 'PATIENT') {
          // Need encrypted fields + wrapped AES key to decrypt
          if (!data.firstNameEncBase64 || !data.lastNameEncBase64 || !data.symmetricKeyEncBase64) {
            this.logger.warn('Missing encrypted profile data for patient', {
              hasFirst: !!data.firstNameEncBase64,
              hasLast: !!data.lastNameEncBase64,
              hasKey: !!data.symmetricKeyEncBase64
            }, 'UserContextService');

            this.firstName = null;
            this.lastName = null;
            return from([data]);
          }

          const keycloakId = this.auth.sub;

          return from(this.keyStore.getRsaPrivateKey(keycloakId)).pipe(
            switchMap((privKey) => {
              if (!privKey) {
                throw new Error('Patient private RSA key not found in IndexedDB (this device)');
              }
              return from(this.crypto.decryptAESKeyWithRSA(data.symmetricKeyEncBase64!, privKey));
            }),
            switchMap((aesKey) =>
              from(Promise.all([
                this.decryptField(data.firstNameEncBase64!, aesKey),
                this.decryptField(data.lastNameEncBase64!, aesKey),
              ]))
            ),
            map(([fn, ln]) => {
              this.firstName = fn;
              this.lastName = ln;
              return data;
            })
          );
        }

        this.firstName = null;
        this.lastName = null;
        return from([data]);
      })
    );
  }

  private async decryptField(encBase64: string, aesKey: CryptoKey): Promise<string> {
    // reuse your proven logic from PatientDataService
    const combined = this.base64ToArrayBuffer(encBase64);
    const combined8 = new Uint8Array(combined);

    const IV_SIZE = 12;
    const iv = combined8.slice(0, IV_SIZE);
    const ciphertext = combined8.slice(IV_SIZE);

    const decrypted = await window.crypto.subtle.decrypt(
      { name: 'AES-GCM', iv },
      aesKey,
      ciphertext
    );

    return new TextDecoder().decode(decrypted);
  }

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
  }
}
