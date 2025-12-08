package be.he2b.healthsec.medical_records.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO pour retourner les informations d'un médecin (pour identification).
 * Ces informations sont nécessaires pour qu'un patient puisse identifier un médecin
 * avant de l'ajouter à sa liste.
 */
@Getter @Setter
public class DoctorInfoDTO {
    private String doctorId;
    private String firstName; // Prénom en clair
    private String lastName; // Nom en clair
    private String medicalOrganization; // Organisation médicale en clair
    private String publicKeyPEM; // Clé publique RSA du médecin
}

