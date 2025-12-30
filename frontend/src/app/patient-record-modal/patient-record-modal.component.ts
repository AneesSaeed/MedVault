import { Component, OnInit, ViewChild, ElementRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { MODAL_DATA } from 'src/app/shared/modal/base-modal/modal.tokens';

import { CryptoService } from '../core/services/crypto.service';
import { KeyStoreService } from '../core/services/key-store.service';
import { AuthService } from '../core/services/auth.service';
import { MedicalFilesApi } from '../core/api/medical-files.api';
import { FileUploadHelper } from '../core/services/file-upload.helper';
import { LoggingService } from '../core/services/logging.service';

interface ApiMedicalFile {
  id: string;
  fileNameEncBase64: string;
  uploadDateEncBase64: string;
  sizeBytes: number;
  wrappedFileKeyEncBase64: string; // wrapped for doctor
}

interface MedicalFileVM extends ApiMedicalFile {
  fileName: string;
  uploadDateIso: string;
}

export interface PatientRecordModalData {
  patientId: string;
  firstName: string;
  lastName: string;
  email: string;
  dob?: string;
}

@Component({
  selector: 'app-patient-record-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './patient-record-modal.component.html',
  styleUrls: ['./patient-record-modal.component.scss'],
})
export class PatientRecordModalComponent implements OnInit {
  public readonly data = inject(MODAL_DATA) as PatientRecordModalData;
  private readonly crypto = inject(CryptoService);
  private readonly auth = inject(AuthService);
  private readonly medicalFilesApi = inject(MedicalFilesApi);
  private readonly keyStore = inject(KeyStoreService);
  private readonly fileUploadHelper = inject(FileUploadHelper);
  private readonly logger = inject(LoggingService);

  filesLoading = false;
  filesError: string | null = null;
  patientFiles: MedicalFileVM[] = [];

  uploadLoading = false;
  uploadError: string | null = null;
  uploadSuccess: string | null = null;
  selectedFile: File | null = null;

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  async ngOnInit(): Promise<void> {
    await this.loadPatientFiles();
  }

  async loadPatientFiles(): Promise<void> {
    this.filesLoading = true;
    this.filesError = null;
    this.patientFiles = [];

    try {
      const doctorPriv = await this.keyStore.getRsaPrivateKey(this.auth.sub);
      if (!doctorPriv) throw new Error('Clé privée du médecin introuvable (IndexedDB)');

      const res = await this.medicalFilesApi.listForDoctor(this.data.patientId).toPromise();
      const files = (res?.files ?? []) as ApiMedicalFile[];

      const vm: MedicalFileVM[] = [];

      for (const f of files) {
        const fileKey = await this.crypto.decryptAESKeyWithRSA(
          f.wrappedFileKeyEncBase64,
          doctorPriv
        );

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

      this.patientFiles = vm;
      this.logger.logAction('PATIENT_FILES_LOADED', '', {
        patientId: this.data.patientId,
        filesCount: this.patientFiles.length
      });
    } catch (e: unknown) {
      const error = e as { message?: string };
      this.filesError = error?.message || 'Impossible de charger les fichiers';
      this.logger.error('Failed to load files for patient', e, { patientId: this.data.patientId });
    } finally {
      this.filesLoading = false;
    }
  }

  download(file: MedicalFileVM): void {
    this.filesLoading = true;
    this.filesError = null;

    (async () => {
      try {
        const doctorPriv = await this.keyStore.getRsaPrivateKey(this.auth.sub);
        if (!doctorPriv) throw new Error('Clé privée du médecin introuvable (IndexedDB)');

        const fileKey = await this.crypto.decryptAESKeyWithRSA(
          file.wrappedFileKeyEncBase64,
          doctorPriv
        );

        this.medicalFilesApi.downloadForDoctor(this.data.patientId, file.id).subscribe({
          next: async (blob) => {
            try {
              const packedBytes = await blob.arrayBuffer();
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
              a.download = file.fileName || 'medical-file';
              a.click();

              URL.revokeObjectURL(url);
            } catch (e: unknown) {
              const error = e as { message?: string };
              this.filesError = error?.message || 'Échec du déchiffrement du fichier';
              this.logger.error('Decrypt file failed', e, { fileId: file.id });
            } finally {
              this.filesLoading = false;
            }
          },
          error: (err) => {
            this.filesError = err?.error?.error || err?.message || 'Téléchargement impossible';
            this.logger.error('Download failed {}: {}', file.id, err?.message || err);
            this.filesLoading = false;
          }
        });
      } catch (e: unknown) {
        const error = e as { message?: string };
        this.filesError = error?.message || 'Téléchargement impossible';
        this.filesLoading = false;
      }
    })();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
    this.uploadError = null;
    this.uploadSuccess = null;
  }

  async uploadRequest(): Promise<void> {
    if (!this.selectedFile) {
      this.uploadError = 'Sélectionnez un fichier d\'abord';
      return;
    }

    this.uploadLoading = true;
    this.uploadError = null;
    this.uploadSuccess = null;

    try {
      const requestId = await this.fileUploadHelper.uploadFileForPatient(
        this.data.patientId,
        this.selectedFile
      );

      this.uploadSuccess = `Demande créée: ${this.selectedFile.name} (ID: ${requestId})`;
      this.selectedFile = null;
      if (this.fileInput?.nativeElement) this.fileInput.nativeElement.value = '';

      // Optional: refresh list if your backend makes it visible immediately
      // await this.loadPatientFiles();
    } catch (e: unknown) {
      const error = e as { message?: string };
      this.uploadError = error?.message || 'Erreur lors de l\'upload';
      this.logger.error('Upload request error: {}', error?.message || e);
    } finally {
      this.uploadLoading = false;
    }
  }

  clearFileSelection(): void {
    this.selectedFile = null;
    this.uploadError = null;
    this.uploadSuccess = null;
    if (this.fileInput?.nativeElement) this.fileInput.nativeElement.value = '';
  }

  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
    return btoa(binary);
  }
}
