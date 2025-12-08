package be.he2b.healthsec.medical_records.dto;

import be.he2b.healthsec.medical_records.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreatePatientDTO {
    private User user;
    private String dateOfBirthEncBase64;

    //important à bien comprendre
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
