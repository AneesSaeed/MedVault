package be.he2b.healthsec.medical_records.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO pour retourner les informations d'un patient vu par un médecin.
 * Contient les données chiffrées du patient + la clé AES chiffrée pour le médecin.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class DoctorPatientDTO {
    
    /**
     * ID du patient (UUID en string)
     */
    private String patientId;
    
    /**
     * Prénom du patient chiffré (Base64)
     */
    private String firstNameEnc;
    
    /**
     * Nom du patient chiffré (Base64)
     */
    private String lastNameEnc;
    
    /**
     * Email du patient chiffré (Base64)
     */
    private String emailEnc;
    
    /**
     * Clé AES du patient chiffrée avec la clé publique RSA du médecin (Base64)
     */
    private String encryptedAESKey;
}
