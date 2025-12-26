import { Component, OnInit } from '@angular/core';
import { PatientDoctorService } from '../core/services/patient-doctor.service';
import { CryptoService } from '../core/services/crypto.service';
import { KeyStoreService } from '../core/services/key-store.service';
import { AuthService } from '../core/services/auth.service';
import { MedicalFilesApi } from '../core/api/medical-files.api';
import { PatientDataService } from '../core/services/patient-data.service';
import { FileUploadHelper } from '../core/services/file-upload.helper';

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

type ApiMedicalFile = {
  id: string;
  fileNameEncBase64: string;
  uploadDateEncBase64: string;
  sizeBytes: number;
  wrappedFileKeyEncBase64: string; // for doctor
};

type MedicalFileVM = ApiMedicalFile & {
  fileName: string;
  uploadDateIso: string;
};

@Component({
    selector: 'app-my-patients',
    templateUrl: './my-patients.component.html',
    styleUrls: ['./my-patients.component.scss'],
    standalone: false
})
export class MyPatientsComponent implements OnInit {
  loading = false;
  error: string | null = null;

  patients: DecryptedPatient[] = [];

  // selection + files (single selected patient)
  selectedPatientId: string | null = null;
  filesLoading = false;
  filesError: string | null = null;
  patientFiles: MedicalFileVM[] = [];

  // upload state
  uploadLoading = false;
  uploadError: string | null = null;
  uploadSuccess: string | null = null;
  selectedFile: File | null = null;

  // details loading per patient (optional)
  detailsLoading: Record<string, boolean> = {};

  constructor(
    private service: PatientDoctorService,
    private crypto: CryptoService,
    private auth: AuthService,
    private medicalFilesApi: MedicalFilesApi,
    private patientDataService: PatientDataService,
    private keyStore: KeyStoreService,
    private fileUploadHelper: FileUploadHelper
  ) {}

  async ngOnInit(): Promise<void> {
    await this.loadAndDecryptPatients();
  }

  private async loadAndDecryptPatients(): Promise<void> {
    this.loading = true;
    this.error = null;

    try {
      // Récupère la liste des IDs patients
      const res = await this.service.getMyPatients().toPromise();
      const list: RawPatient[] = (res?.patients ?? []) as RawPatient[];

      const decrypted: DecryptedPatient[] = [];

      // Pour chaque patient, utilise PatientDataService pour déchiffrer
      for (const item of list) {
        try {
          const patientData = await this.patientDataService.getPatientData(
            item.patientId,
            this.auth.sub
          );

          decrypted.push({
            patientId: item.patientId,
            firstName: patientData.firstName,
            lastName: patientData.lastName,
            email: patientData.email,
            dob: patientData.dateOfBirth
          });
        } catch (e: any) {
          console.error(`Failed to decrypt patient ${item.patientId}:`, e);
          // On ajoute quand même avec données partielles
          decrypted.push({
            patientId: item.patientId,
            firstName: '(erreur déchiffrement)',
            lastName: '',
            email: ''
          });
        }
      }

      this.patients = decrypted;
    } catch (e: any) {
      console.error(e);
      this.error = e?.message || 'Impossible de charger les patients';
    } finally {
      this.loading = false;
    }
  }

  // Click patient => load files for that patient
  async selectPatient(p: DecryptedPatient): Promise<void> {
    if (this.selectedPatientId === p.patientId && this.patientFiles.length) return;
    await this.loadPatientFiles(p.patientId);
  }

  async loadDetails(p: DecryptedPatient): Promise<void> {
    // Les détails sont déjà chargés via PatientDataService dans loadAndDecryptPatients
    // Cette méthode n'est plus nécessaire mais on la garde pour compatibilité
    if (this.detailsLoading[p.patientId]) return;
    this.detailsLoading[p.patientId] = true;

    try {
      // Recharge les données si besoin
      const patientData = await this.patientDataService.getPatientData(
        p.patientId,
        this.auth.sub
      );
      p.dob = patientData.dateOfBirth;
    } catch (e) {
      console.error('Failed to load patient details:', e);
    } finally {
      this.detailsLoading[p.patientId] = false;
    }
  }

  async loadPatientFiles(patientId: string): Promise<void> {
    this.selectedPatientId = patientId;
    this.filesLoading = true;
    this.filesError = null;
    this.patientFiles = [];

    try {
      const doctorPriv = await this.keyStore.getRsaPrivateKey(this.auth.sub);
      if (!doctorPriv) throw new Error('Clé privée du médecin introuvable (IndexedDB)');


      const res = await this.medicalFilesApi.listForDoctor(patientId).toPromise();
      const files = (res?.files ?? []) as ApiMedicalFile[];

      const vm: MedicalFileVM[] = [];

      for (const f of files) {
        const fileKey = await this.crypto.decryptAESKeyWithRSA(f.wrappedFileKeyEncBase64, doctorPriv);

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
    } catch (e: any) {
      console.error(e);
      this.filesError = e?.message || 'Impossible de charger les fichiers';
    } finally {
      this.filesLoading = false;
    }
  }

  downloadPatientFile(file: MedicalFileVM) {
    if (!this.selectedPatientId) return;

    this.filesLoading = true;
    this.filesError = null;

    (async () => {
      try {
        const doctorPriv = await this.keyStore.getRsaPrivateKey(this.auth.sub);
        if (!doctorPriv) throw new Error('Clé privée du médecin introuvable (IndexedDB)');

        const fileKey = await this.crypto.decryptAESKeyWithRSA(file.wrappedFileKeyEncBase64, doctorPriv);

        this.medicalFilesApi.downloadForDoctor(this.selectedPatientId!, file.id).subscribe({
          next: async (blob) => {
            try {
              const packedBytes = await blob.arrayBuffer();
              const packedBase64 = this.arrayBufferToBase64(packedBytes);

              const parts = this.crypto.unpackIvCipherFromBase64(packedBase64);
              const decrypted = await this.crypto.decryptBytesWithAES(parts.encryptedBase64, parts.ivBase64, fileKey);

              const outBlob = new Blob([decrypted]);
              const url = URL.createObjectURL(outBlob);

              const a = document.createElement('a');
              a.href = url;
              a.download = file.fileName || 'medical-file';
              a.click();

              URL.revokeObjectURL(url);
            } catch (e: any) {
              this.filesError = e?.message || 'Échec du déchiffrement du fichier';
            } finally {
              this.filesLoading = false;
            }
          },
          error: (err) => {
            this.filesError = err?.error?.error || err?.message || 'Téléchargement impossible';
            this.filesLoading = false;
          }
        });
      } catch (e: any) {
        this.filesError = e?.message || 'Téléchargement impossible';
        this.filesLoading = false;
      }
    })();
  }

  // ===== UPLOAD FILE SECTION =====

  /**
   * Gère la sélection d'un fichier par le docteur
   */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
      this.uploadError = null;
      this.uploadSuccess = null;
    }
  }

  /**
   * Upload un fichier au patient sélectionné
   * Processus:
   * 1. Crée une clé AES temporaire
   * 2. Chiffre le fichier avec cette clé
   * 3. Chiffre la clé avec la clé publique RSA du patient
   * 4. Envoie la demande au serveur
   */
  async uploadFileToPatient(): Promise<void> {
    if (!this.selectedPatientId) {
      this.uploadError = 'Sélectionnez un patient d\'abord';
      return;
    }
    if (!this.selectedFile) {
      this.uploadError = 'Sélectionnez un fichier d\'abord';
      return;
    }

    this.uploadLoading = true;
    this.uploadError = null;
    this.uploadSuccess = null;

    try {
      const requestId = await this.fileUploadHelper.uploadFileForPatient(
        this.selectedPatientId,
        this.selectedFile
      );

      this.uploadSuccess = `Demande d'upload créée: ${this.selectedFile.name} (ID: ${requestId})`;
      this.selectedFile = null;

      // Réinitialiser le formulaire
      const input = document.querySelector('input[type="file"]') as HTMLInputElement;
      if (input) input.value = '';
    } catch (e: any) {
      this.uploadError = e?.message || 'Erreur lors de l\'upload';
      console.error('Upload error:', e);
    } finally {
      this.uploadLoading = false;
    }
  }

  /**
   * Annule la sélection du fichier
   */
  clearFileSelection(): void {
    this.selectedFile = null;
    this.uploadError = null;
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    if (input) input.value = '';
  }


  // helpers
  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
    return btoa(binary);
  }

  // removed unused decryptCombinedAESField

}
