import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../core/services/auth.service';
import { PendingFileHelper, DecryptedPendingFile } from '../core/services/pending-file.helper';
import { LoggingService } from '../core/services/logging.service';
import { PatientDoctorService } from '../core/services/patient-doctor.service';
import { sanitizeFilename } from '../core/utils/sanitize.util';

interface DoctorLite {
  doctorId: string;
  firstName: string;
  lastName: string;
}

@Component({
  selector: 'app-pending-medical-files',
  templateUrl: './pending-medical-files.component.html',
  styleUrls: ['./pending-medical-files.component.scss'],
  standalone: true,
  imports: [CommonModule]
})
export class PendingMedicalFilesComponent implements OnInit {
  loading = false;
  error: string | null = null;
  message: string | null = null;

  pendingFiles: DecryptedPendingFile[] = [];

  // Track ongoing operations
  viewingId: string | null = null;
  rejectingId: string | null = null;
  acceptingId: string | null = null;

  // Doctor info for display (cached)
  doctorInfo = new Map<string, { firstName: string; lastName: string }>();


  private auth = inject(AuthService);
  private pendingFileHelper = inject(PendingFileHelper);
  private logger = inject(LoggingService);
  private patientDoctorService = inject(PatientDoctorService);

  async ngOnInit(): Promise<void> {
    await this.loadPendingRequests();
  }

  /**
   * Charge la liste des demandes d'upload en attente
   */
  private async loadPendingRequests(): Promise<void> {
    this.loading = true;
    this.error = null;
    this.message = null;

    try {
      const keycloakId = this.auth.sub;
      this.logger.debug('Loading pending requests', { keycloakId }, 'PendingMedicalFilesComponent');

      this.pendingFiles = await this.pendingFileHelper.listPendingRequests(keycloakId);

      // Best-effort: resolve doctor names (non-blocking errors)
      await this.resolveDoctorNames();

      this.logger.info('Loaded pending requests', { count: this.pendingFiles.length }, 'PendingMedicalFilesComponent');
    } catch (e: unknown) {
      this.logger.error('Error loading pending requests', e, {}, 'PendingMedicalFilesComponent');
      this.error = (typeof e === 'object' && e && 'message' in e && typeof (e as { message: unknown }).message === 'string')
        ? (e as { message: string }).message
        : 'Impossible de charger les demandes';
    } finally {
      this.loading = false;
    }
  }

  private async resolveDoctorNames(): Promise<void> {
    try {
      const uploaderIds = Array.from(
        new Set(this.pendingFiles.map(f => f.uploaderDoctorId).filter(Boolean))
      );

      if (uploaderIds.length === 0) return;

      const allDoctorsRes = await this.patientDoctorService.listAllDoctors().toPromise();
      const allDoctors: DoctorLite[] = (allDoctorsRes?.doctors ?? []) as DoctorLite[];

      const map = new Map<string, { firstName: string; lastName: string }>();
      for (const d of allDoctors) {
        if (uploaderIds.includes(d.doctorId)) {
          map.set(d.doctorId, { firstName: d.firstName, lastName: d.lastName });
        }
      }
      this.doctorInfo = map;
    } catch (e: unknown) {
      // Do not fail the page for name resolution issues
      this.logger.debug(
        'Failed to resolve doctor names for pending files',
        { message: (typeof e === 'object' && e && 'message' in e && typeof (e as { message: unknown }).message === 'string')
            ? (e as { message: string }).message
            : String(e) },
        'PendingMedicalFilesComponent'
      );
    }
  }

  getDoctorDisplay(uploaderDoctorId: string): string {
    const info = this.doctorInfo.get(uploaderDoctorId);
    if (info) return `Dr ${info.firstName} ${info.lastName}`;
    const short = (uploaderDoctorId || '').slice(0, 8);
    return short ? `Médecin ${short}…` : 'Médecin';
  }

  /**
   * Visualise (télécharge) le fichier sans l'ajouter au dossier médical
   */
  async viewFile(file: DecryptedPendingFile): Promise<void> {
    this.viewingId = file.id;
    this.error = null;
    this.message = null;

    try {
      this.logger.debug(
        'Viewing pending file',
        { fileId: file.id, fileName: file.fileName },
        'PendingMedicalFilesComponent'
      );

      const keycloakId = this.auth.sub;

      const decryptedBlob = await this.pendingFileHelper.viewFile(keycloakId, file);

      const url = URL.createObjectURL(decryptedBlob);
      const a = document.createElement('a');
      a.href = url;
      a.download = sanitizeFilename(file.fileName || 'medical-file');
      a.click();
      URL.revokeObjectURL(url);

      this.message = `Fichier téléchargé pour visualisation: ${file.fileName}`;
      this.logger.info(
        'Pending file viewed (downloaded)',
        { fileId: file.id, fileName: file.fileName },
        'PendingMedicalFilesComponent'
      );
    } catch (e: unknown) {
      this.error = (typeof e === 'object' && e && 'message' in e && typeof (e as { message: unknown }).message === 'string')
        ? (e as { message: string }).message
        : 'Erreur lors de la visualisation';
      this.logger.error('View error', e, { fileId: file.id }, 'PendingMedicalFilesComponent');
    } finally {
      this.viewingId = null;
    }
  }

  /**
   * Rejette (refuse) une demande
   */
  async rejectRequest(file: DecryptedPendingFile): Promise<void> {
    if (!confirm(`Rejeter la demande pour "${file.fileName}" ?`)) return;

    this.rejectingId = file.id;
    this.error = null;
    this.message = null;

    try {
      this.logger.debug(
        'Rejecting pending request',
        { fileId: file.id, fileName: file.fileName },
        'PendingMedicalFilesComponent'
      );

      await this.pendingFileHelper.rejectRequest(file.id);

      this.message = `Demande rejetée: ${file.fileName}`;
      this.logger.logAction(
        'PENDING_FILE_REJECTED',
        this.auth.sub,
        { fileId: file.id, fileName: file.fileName },
        'PendingMedicalFilesComponent'
      );

      this.pendingFiles = this.pendingFiles.filter(f => f.id !== file.id);
    } catch (e: unknown) {
      this.error = (typeof e === 'object' && e && 'message' in e && typeof (e as { message: unknown }).message === 'string')
        ? (e as { message: string }).message
        : 'Erreur lors du rejet';
      this.logger.error('Reject error', e, { fileId: file.id }, 'PendingMedicalFilesComponent');
    } finally {
      this.rejectingId = null;
    }
  }

  /**
   * Accepte une demande, ajoute le fichier au dossier médical et le partage avec tous les médecins
   */
  async acceptRequest(file: DecryptedPendingFile): Promise<void> {
    if (
      !confirm(
        `Accepter et ajouter "${file.fileName}" à votre dossier médical ?\n\nLe fichier sera partagé avec tous vos médecins.`
      )
    ) {
      return;
    }

    this.acceptingId = file.id;
    this.error = null;
    this.message = null;

    try {
      this.logger.debug(
        'Accepting pending request',
        { fileId: file.id, fileName: file.fileName },
        'PendingMedicalFilesComponent'
      );

      const keycloakId = this.auth.sub;

      await this.pendingFileHelper.acceptAndAddToMedicalRecord(keycloakId, file);

      this.message = `Fichier "${file.fileName}" ajouté à votre dossier médical et partagé avec vos médecins`;
      this.logger.logAction(
        'PENDING_FILE_ACCEPTED_AND_SHARED',
        keycloakId,
        { fileId: file.id, fileName: file.fileName },
        'PendingMedicalFilesComponent'
      );

      this.pendingFiles = this.pendingFiles.filter(f => f.id !== file.id);
    } catch (e: unknown) {
      this.error = (typeof e === 'object' && e && 'message' in e && typeof (e as { message: unknown }).message === 'string')
        ? (e as { message: string }).message
        : "Erreur lors de l'acceptation";
      this.logger.error('Accept error', e, { fileId: file.id }, 'PendingMedicalFilesComponent');
    } finally {
      this.acceptingId = null;
    }
  }

  /**
   * Formate la taille du fichier pour l'affichage
   */
  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  }

  /**
   * Formate la date pour l'affichage
   */
  formatDate(isoString: string): string {
    try {
      const date = new Date(isoString);
      return date.toLocaleString('fr-FR');
    } catch {
      return isoString;
    }
  }
}
