/**
 * Modèle représentant les données déchiffrées d'un patient.
 * 
 * Ces données sont:
 * - Téléchargées chiffrées depuis le backend
 * - Déchiffrées côté client avec la clé privée RSA
 * - Affichées à l'utilisateur
 */
export interface PatientData {
  patientId: string;
  firstName: string;
  lastName: string;
  email: string;
  dateOfBirth: string;
}
