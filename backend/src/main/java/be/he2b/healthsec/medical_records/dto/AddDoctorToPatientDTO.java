package be.he2b.healthsec.medical_records.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO pour ajouter un médecin à la liste des médecins d'un patient.
 * 
 * Selon l'énoncé : "A patient can add or remove a doctor to his list of appointed doctors."
 * Note: Les informations du médecin (nom, prénom, organisation) sont en clair,
 * donc pas besoin de les chiffrer pour le patient.
 */
@Getter @Setter
public class AddDoctorToPatientDTO {
    /**
     * ID du médecin à ajouter
     */
    @NotBlank(message = "doctorId is required")
    private String doctorId;
    
    /**
     * Clé AES du patient chiffrée avec la clé publique RSA du médecin (en base64).
     * Cette clé permet au médecin de déchiffrer les dossiers médicaux du patient.
     */
    @NotBlank(message = "encryptedPatientAESKeyBase64 is required")
    private String encryptedPatientAESKeyBase64;
}

