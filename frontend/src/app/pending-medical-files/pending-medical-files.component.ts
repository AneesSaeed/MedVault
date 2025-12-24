import { Component, OnInit } from '@angular/core';
import { AuthService } from '../core/services/auth.service';
import { PendingFileHelper, DecryptedPendingFile } from '../core/services/pending-file.helper';
// import { UserContextService } from '../core/services/user-context.service';
import { sanitizeFilename } from '../core/utils/sanitize.util';

@Component({
  selector: 'app-pending-medical-files',
  templateUrl: './pending-medical-files.component.html',
  styleUrls: ['./pending-medical-files.component.scss']
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
  doctorInfo: Map<string, { firstName: string; lastName: string }> = new Map();

  constructor(
    private auth: AuthService,
    private pendingFileHelper: PendingFileHelper
  ) {}

  async ngOnInit() {
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
      this.pendingFiles = await this.pendingFileHelper.listPendingRequests(keycloakId);

      if (this.pendingFiles.length === 0) {
        this.message = 'Aucune demande en attente';
      }
    } catch (e: any) {
      console.error('Error loading pending requests:', e);
      this.error = e?.message || 'Impossible de charger les demandes';
    } finally {
      this.loading = false;
    }
  }

  /**
   * Visualise (télécharge) le fichier sans l'ajouter au dossier médical
   */
  async viewFile(file: DecryptedPendingFile): Promise<void> {
    this.viewingId = file.id;
    this.error = null;
    this.message = null;

    try {
      const keycloakId = this.auth.sub;

      // Déchiffre le fichier
      const decryptedBlob = await this.pendingFileHelper.viewFile(
        keycloakId,
        file
      );

      // Télécharge le fichier pour prévisualisation
      const url = URL.createObjectURL(decryptedBlob);
      const a = document.createElement('a');
      a.href = url;
      a.download = sanitizeFilename(file.fileName || 'medical-file');
      a.click();
      URL.revokeObjectURL(url);

      this.message = `Fichier téléchargé pour visualisation: ${file.fileName}`;
    } catch (e: any) {
      this.error = e?.message || 'Erreur lors de la visualisation';
      console.error('View error:', e);
    } finally {
      this.viewingId = null;
    }
  }

  /**
   * Rejette (refuse) une demande
   */
  async rejectRequest(file: DecryptedPendingFile): Promise<void> {
    if (!confirm(`Rejeter la demande pour "${file.fileName}" ?`)) {
      return;
    }

    this.rejectingId = file.id;
    this.error = null;
    this.message = null;

    try {
      await this.pendingFileHelper.rejectRequest(file.id);
      this.message = `Demande rejetée: ${file.fileName}`;

      // Retirer de la liste
      this.pendingFiles = this.pendingFiles.filter(f => f.id !== file.id);
    } catch (e: any) {
      this.error = e?.message || 'Erreur lors du rejet';
      console.error('Reject error:', e);
    } finally {
      this.rejectingId = null;
    }
  }

  /**
   * Accepte une demande, ajoute le fichier au dossier médical et le partage avec tous les médecins
   */
  async acceptRequest(file: DecryptedPendingFile): Promise<void> {
    if (!confirm(`Accepter et ajouter "${file.fileName}" à votre dossier médical ?\n\nLe fichier sera partagé avec tous vos médecins.`)) {
      return;
    }

    this.acceptingId = file.id;
    this.error = null;
    this.message = null;

    try {
      const keycloakId = this.auth.sub;

      // Accepte et ajoute au dossier médical + partage avec tous les médecins
      await this.pendingFileHelper.acceptAndAddToMedicalRecord(
        keycloakId,
        file
      );

      this.message = `Fichier "${file.fileName}" ajouté à votre dossier médical et partagé avec vos médecins`;

      // Retirer de la liste
      this.pendingFiles = this.pendingFiles.filter(f => f.id !== file.id);
    } catch (e: any) {
      this.error = e?.message || 'Erreur lors de l\'acceptation';
      console.error('Accept error:', e);
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
