package be.he2b.healthsec.medical_records.dto;

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
    private String firstName;
    
    /**
     * Nom du médecin (en clair).
     */
    private String lastName;
    
    /**
     * Email du médecin (en clair).
     */
    private String email;
    
    /**
     * Organisation médicale (en clair).
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