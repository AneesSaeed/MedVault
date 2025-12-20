package be.he2b.healthsec.medical_records.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreatePatientDTO {
    
    /**
     * SÉCURITÉ: Toutes les données personnelles du patient sont chiffrées
     * avec sa clé AES avant d'être envoyées au serveur (encodées en Base64).
     * 
     * Les données en clair ne sont JAMAIS stockées sur le serveur pour éviter
     * l'énumération des patients via accès direct à la base de données.
     */
    
    /**
     * Prénom du patient chiffré avec sa clé AES, encodé en Base64.
     */
    private String firstNameEncBase64;
    
    /**
     * Nom du patient chiffré avec sa clé AES, encodé en Base64.
     */
    private String lastNameEncBase64;
    
    /**
     * Email du patient chiffré avec sa clé AES, encodé en Base64.
     */
    private String emailEncBase64;
    
    /**
     * Date de naissance du patient chiffrée avec sa clé AES, encodée en Base64.
     */
    private String dateOfBirthEncBase64;

    /**
     * Clé publique RSA de l'utilisateur en format PEM.
     * Cette clé sera utilisée pour chiffrer la clé AES du patient
     * lors du partage avec les médecins (dans PatientDoctor).
     */
    private String publicKeyPEM;
    
    // NOTE: La clé AES du patient n'est PAS envoyée au serveur lors de la création.
    // Elle reste dans le localStorage côté client.
    // Elle sera partagée avec les médecins uniquement lors de la création
    // de la relation PatientDoctor, chiffrée avec la clé publique RSA du médecin.
}
