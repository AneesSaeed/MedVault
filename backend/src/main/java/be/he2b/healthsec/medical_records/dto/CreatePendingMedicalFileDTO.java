package be.he2b.healthsec.medical_records.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour créer une demande d'ajout de fichier médical par un docteur.
 * Toutes les données binaires sont en Base64.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePendingMedicalFileDTO {
    private String fileNameEncBase64; // optionnel
    private String contentEncBase64;
    private String ivBase64;
    private String wrappedTempKeyForPatientBase64;
    private String mimeTypeEncBase64; // optionnel
}
