package be.he2b.healthsec.medical_records.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO pour retourner les informations d'un patient (pour identification).
 * Ces informations sont nécessaires pour qu'un médecin puisse identifier un patient
 * avant de demander à être ajouté.
 */
@Getter @Setter
public class PatientInfoDTO {
    private String patientId;
    private String firstName; // Prénom en clair
    private String lastName; // Nom en clair
    private String publicKeyPEM; // Clé publique RSA du patient
}

