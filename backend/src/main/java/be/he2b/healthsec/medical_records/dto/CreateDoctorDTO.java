package be.he2b.healthsec.medical_records.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateDoctorDTO {
    
    /**
     * SÉCURITÉ: Toutes les données du médecin sont stockées EN CLAIR
     * car les médecins sont "découvrables" par les patients pour l'identification.
     */
    
    /**
     * Prénom du médecin (en clair).
     */
    @NotBlank(message = "firstName is required")
    private String firstName;
    
    /**
     * Nom du médecin (en clair).
     */
    @NotBlank(message = "lastName is required")
    private String lastName;
    
    /**
     * Email du médecin (en clair).
     */
    @Email(message = "email must be valid")
    @NotBlank(message = "email is required")
    private String email;
    
    /**
     * Organisation médicale (en clair).
     * Les informations des médecins sont stockées en clair pour permettre
     * aux patients de les identifier et de les rechercher.
     */
    @NotBlank(message = "medicalOrganization is required")
    private String medicalOrganization;
    
    /**
     * Clé publique RSA de l'utilisateur en format PEM.
     * Cette clé sera utilisée pour chiffrer la clé AES du patient
     * lors du partage (dans PatientDoctor).
     */
    @NotBlank(message = "publicKeyPEM is required")
    private String publicKeyPEM;
}