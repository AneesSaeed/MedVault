import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';

import { UserService } from '../core/services/user.service';
import { UserContextService } from '../core/services/user-context.service';
import { AuthService } from '../core/services/auth.service';

import { MedicalFilesApi } from '../core/api/medical-files.api';
import { MedicalFile } from '../core/models/medical-file.model';
import { CryptoService } from '../core/services/crypto.service';

type MedicalFileVM = MedicalFile & {
  fileName: string;
  uploadDateIso: string;
};


@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss'],
})
export class HomeComponent implements OnInit {
  files: MedicalFile[] = [];
  loading = false;
  error: string | null = null;

  selectedNewFile: File | null = null;
  overwriteSelection: Record<string, File | null> = {};

  filesVm: MedicalFileVM[] = [];

  constructor(
    private userService: UserService,
    public userContext: UserContextService,
    private auth: AuthService,
    private router: Router,
    private medicalFilesApi: MedicalFilesApi,
    private crypto: CryptoService
  ) {}

  get role(): 'PATIENT' | 'DOCTOR' | string {
    return this.userContext.role ?? 'Loading...';
  }

  // Use Keycloak subject as key id for AES storage/lookup
  get keyId(): string {
    return this.auth.sub || 'me';
  }

  ngOnInit(): void {
    this.userService.userExists().subscribe({
      next: (exists) => {
        if (!exists) {
          this.router.navigate(['/onboarding']);
          return;
        }

        this.userContext.loadUserContext$().subscribe({
          next: () => {
            if (this.role === 'PATIENT') {
              this.refresh();
            }
          },
          error: () => {
            this.error = 'Failed to load user context';
          }
        });
      },
      error: () => {
        this.error = 'Failed to check user';
      }
    });
  }


  refresh(): void {
    this.loading = true;
    this.error = null;

    this.medicalFilesApi.list().subscribe({
      next: async (res) => {
        try {
          const privPem = this.crypto.getPrivateKey(this.keyId);
          if (!privPem) throw new Error('Patient private key not found in localStorage');
          const privKey = await this.crypto.importPrivateKey(privPem);

          const files = res.files ?? [];
          const vm: MedicalFileVM[] = [];

          for (const f of files) {
            // 1) unwrap per-file AES key
            const fileKey = await this.crypto.decryptAESKeyWithRSA(
              f.wrappedFileKeyEncBase64,
              privKey
            );

            // 2) decrypt metadata with per-file key
            const nameParts = this.crypto.unpackIvCipherFromBase64(f.fileNameEncBase64);
            const dateParts = this.crypto.unpackIvCipherFromBase64(f.uploadDateEncBase64);

            const fileName = await this.crypto.decryptWithAES(
              nameParts.encryptedBase64,
              nameParts.ivBase64,
              fileKey
            );

            const uploadDateIso = await this.crypto.decryptWithAES(
              dateParts.encryptedBase64,
              dateParts.ivBase64,
              fileKey
            );

            vm.push({ ...f, fileName, uploadDateIso });
          }

          this.filesVm = vm;
        } catch (e: any) {
          this.filesVm = [];
          this.error = e?.message || 'Failed to decrypt file list';
        } finally {
          this.loading = false;
        }
      },
      error: (err) => {
        this.error = err?.error?.error || err?.message || 'Failed to load files';
        this.loading = false;
      },
    });
  }


  onNewFileChange(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;

    this.error = null;
    this.selectedNewFile = null;

    if (!file) return;

    const err = this.fileError(file);
    if (err) {
      this.error = err;
      input.value = ''; // reset input
      return;
    }

    this.selectedNewFile = file;
  }


  async upload(): Promise<void> {
    if (!this.selectedNewFile) return;

    this.loading = true;
    this.error = null;

    try {
      const file = this.selectedNewFile;

      // Patient public RSA key
      const pubPem = this.crypto.getPublicKey(this.keyId);
      if (!pubPem) throw new Error('Patient public key not found in localStorage');
      const pubKey = await this.crypto.importPublicKey(pubPem);

      // 1) per-file AES key
      const fileKey = await this.crypto.generateAESKey();

      // 2) encrypt metadata with per-file key
      const nameEnc = await this.crypto.encryptWithAES(file.name, fileKey);
      const dateEnc = await this.crypto.encryptWithAES(new Date().toISOString(), fileKey);

      const fileNameEncBase64 = this.crypto.packIvCipherToBase64(nameEnc.iv, nameEnc.encrypted);
      const uploadDateEncBase64 = this.crypto.packIvCipherToBase64(dateEnc.iv, dateEnc.encrypted);

      // 3) encrypt bytes with per-file key
      const bytes = await file.arrayBuffer();
      const encFile = await this.crypto.encryptBytesWithAES(bytes, fileKey);
      const packed = this.crypto.packIvAndCiphertext(encFile.iv, encFile.encrypted);
      const blob = new Blob([packed], { type: 'application/octet-stream' });

      // 4) wrap per-file key for patient using RSA public key
      const wrappedKeyForPatientBase64 = await this.crypto.encryptAESKeyWithRSA(fileKey, pubKey);

      const form = new FormData();
      form.append('fileNameEncBase64', fileNameEncBase64);
      form.append('uploadDateEncBase64', uploadDateEncBase64);
      form.append('wrappedKeyForPatientBase64', wrappedKeyForPatientBase64);
      form.append('file', blob, 'content.enc');

      this.medicalFilesApi.upload(form).subscribe({
        next: () => {
          this.selectedNewFile = null;
          this.loading = false;
          this.refresh();
        },
        error: (err) => {
          this.error = err?.error?.error || err?.message || 'Upload failed';
          this.loading = false;
        },
      });
    } catch (e: any) {
      this.error = e?.message || 'Upload failed';
      this.loading = false;
    }
  }


  onOverwriteFileChange(fileId: string, ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;

    this.error = null;
    this.overwriteSelection[fileId] = null;

    if (!file) return;

    const err = this.fileError(file);
    if (err) {
      this.error = err;
      input.value = '';
      return;
    }

    this.overwriteSelection[fileId] = file;
  }


  async overwrite(f: MedicalFileVM): Promise<void> {
    const file = this.overwriteSelection[f.id];
    if (!file) return;

    const err = this.fileError(file);
    if (err) {
      this.error = err;
      return;
    }

    this.loading = true;
    this.error = null;

    try {
      // Patient private key
      const privPem = this.crypto.getPrivateKey(this.keyId);
      if (!privPem) throw new Error('Patient private key not found in localStorage');
      const privKey = await this.crypto.importPrivateKey(privPem);

      // unwrap per-file key
      const fileKey = await this.crypto.decryptAESKeyWithRSA(
        f.wrappedFileKeyEncBase64,
        privKey
      );

      // encrypt new upload date with per-file key
      const dateEnc = await this.crypto.encryptWithAES(new Date().toISOString(), fileKey);
      const uploadDateEncBase64 = this.crypto.packIvCipherToBase64(dateEnc.iv, dateEnc.encrypted);

      // encrypt bytes with per-file key
      const bytes = await file.arrayBuffer();
      const encFile = await this.crypto.encryptBytesWithAES(bytes, fileKey);
      const packed = this.crypto.packIvAndCiphertext(encFile.iv, encFile.encrypted);
      const blob = new Blob([packed], { type: 'application/octet-stream' });

      const form = new FormData();
      form.append('uploadDateEncBase64', uploadDateEncBase64);
      form.append('file', blob, 'content.enc');

      this.medicalFilesApi.overwrite(f.id, form).subscribe({
        next: () => {
          this.overwriteSelection[f.id] = null;
          this.loading = false;
          this.refresh();
        },
        error: (err2) => {
          this.error = err2?.error?.error || err2?.message || 'Overwrite failed';
          this.loading = false;
        },
      });
    } catch (e: any) {
      this.error = e?.message || 'Overwrite failed';
      this.loading = false;
    }
  }


  delete(fileId: string): void {
    const ok = confirm('Delete this file permanently?');
    if (!ok) return;

    this.loading = true;
    this.error = null;

    this.medicalFilesApi.delete(fileId).subscribe({
      next: () => {
        this.loading = false;
        this.refresh();
      },
      error: (err) => {
        this.error = err?.error?.error || err?.message || 'Delete failed';
        this.loading = false;
      },
    });
  }

  download(f: MedicalFileVM): void {
    this.loading = true;
    this.error = null;

    (async () => {
      try {
        const privPem = this.crypto.getPrivateKey(this.keyId);
        if (!privPem) throw new Error('Patient private key not found in localStorage');
        const privKey = await this.crypto.importPrivateKey(privPem);

        // unwrap per-file key
        const fileKey = await this.crypto.decryptAESKeyWithRSA(
          f.wrappedFileKeyEncBase64,
          privKey
        );

        this.medicalFilesApi.download(f.id).subscribe({
          next: async (blob) => {
            try {
              const packedBytes = await blob.arrayBuffer();

              // packed bytes are [iv|ciphertext]
              const packedBase64 = this.arrayBufferToBase64(packedBytes);
              const parts = this.crypto.unpackIvCipherFromBase64(packedBase64);

              const decrypted = await this.crypto.decryptBytesWithAES(
                parts.encryptedBase64,
                parts.ivBase64,
                fileKey
              );

              const outBlob = new Blob([decrypted]);
              const url = URL.createObjectURL(outBlob);

              const a = document.createElement('a');
              a.href = url;
              a.download = f.fileName || 'medical-file';
              a.click();

              URL.revokeObjectURL(url);
            } catch (e: any) {
              this.error = e?.message || 'Failed to decrypt downloaded file';
            } finally {
              this.loading = false;
            }
          },
          error: (err) => {
            this.error = err?.error?.error || err?.message || 'Download failed';
            this.loading = false;
          },
        });
      } catch (e: any) {
        this.error = e?.message || 'Download failed';
        this.loading = false;
      }
    })();
  }

  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
    return btoa(binary);
  }

  private readonly MAX_BYTES = 10 * 1024 * 1024; // 10 MB

  private readonly ALLOWED_MIME = new Set([
    'text/plain',
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'image/jpeg',
    'image/png',
  ]);

  private readonly ALLOWED_EXT = new Set(['txt', 'pdf', 'doc', 'docx', 'jpeg', 'jpg', 'png']);

  private isAllowedFile(file: File): boolean {
    const ext = (file.name.split('.').pop() || '').toLowerCase();
    const mimeOk = this.ALLOWED_MIME.has(file.type);
    const extOk = this.ALLOWED_EXT.has(ext);
    return (mimeOk || extOk) && file.size <= this.MAX_BYTES;
  }

  private fileError(file: File): string | null {
    if (file.size > this.MAX_BYTES) return 'File too large (max 10 MB).';

    const ext = (file.name.split('.').pop() || '').toLowerCase();
    const mimeOk = this.ALLOWED_MIME.has(file.type);
    const extOk = this.ALLOWED_EXT.has(ext);

    if (!mimeOk && !extOk) {
      return 'Unsupported file type. Allowed: .txt, .pdf, .doc, .docx, .jpg/.jpeg, .png';
    }

    return null;
  }
}
