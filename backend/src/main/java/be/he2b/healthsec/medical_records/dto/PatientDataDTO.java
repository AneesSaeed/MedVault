package be.he2b.healthsec.medical_records.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO pour retourner les données d'un patient avec sa clé symétrique chiffrée.
 * 
 * Utilisé quand:
 * - Un patient consulte son propre profil
 * - Un docteur consulte les données d'un patient (auquel il a accès)
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class PatientDataDTO {
    
    /**
     * ID du patient (UUID)
     */
    private String patientId;
    
    /**
     * Prénom du patient, chiffré avec sa clé AES, encodé en Base64
     */
    private String firstNameEncBase64;
    
    /**
     * Nom du patient, chiffré avec sa clé AES, encodé en Base64
     */
    private String lastNameEncBase64;
    
    /**
     * Email du patient, chiffré avec sa clé AES, encodé en Base64
     */
    private String emailEncBase64;
    
    /**
     * Date de naissance du patient, chiffrée avec sa clé AES, encodée en Base64
     */
    private String dateOfBirthEncBase64;
    
    /**
     * Clé AES du patient, chiffrée avec la clé publique RSA du destinataire (patient ou docteur),
     * encodée en Base64.
     * 
     * Format: RSA-OAEP encrypted AES-256 key, Base64 encoded
     * 
     * Pour déchiffrer:
     * 1. Décoder de Base64
     * 2. Déchiffrer avec la clé privée RSA du destinataire (côté client)
     * 3. Utiliser K_patient en clair pour déchiffrer les données patient
     */
    private String symmetricKeyEncBase64;
}
