package be.he2b.healthsec.medical_records.dto;

import be.he2b.healthsec.medical_records.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateDoctorDTO {
    private User user;
    /**
     * Organisation médicale en clair (pas de chiffrement nécessaire).
     * Les informations des médecins sont stockées en clair pour permettre
     * aux patients de les identifier et de les rechercher.
     */
    private String medicalOrganization;
    /**
     * Clé publique RSA de l'utilisateur en format PEM.
     * Cette clé sera utilisée pour chiffrer la clé AES du patient
     * lors du partage (dans PatientDoctor).
     */
    private String publicKeyPEM;
}