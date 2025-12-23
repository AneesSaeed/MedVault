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
     * Cette clé sera utilisée pour chiffrer la clé AES du patient.
     */
    private String publicKeyPEM;

    /**
     * NOUVEAU: Clé symétrique AES du patient, chiffrée avec la clé publique RSA du patient,
     * puis encodée en Base64.
     * 
     * Cette clé symétrique chiffre TOUTES les données personnelles du patient 
     * (firstName, lastName, email, dateOfBirth) et sera partagée avec tous les docteurs.
     * 
     * Format: RSA-OAEP encrypted AES-256 key, Base64 encoded
     */
    private String symmetricKeyEncBase64;
}
