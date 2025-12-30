package be.he2b.healthsec.medical_records.dto;

import jakarta.validation.constraints.NotBlank;
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
    @NotBlank(message = "fileNameEncBase64 is required")
    private String fileNameEncBase64;

    @NotBlank(message = "contentEncBase64 is required")
    private String contentEncBase64;

    @NotBlank(message = "ivBase64 is required")
    private String ivBase64;

    @NotBlank(message = "wrappedTempKeyForPatientBase64 is required")
    private String wrappedTempKeyForPatientBase64;

    @NotBlank(message = "mimeTypeEncBase64 is required")
    private String mimeTypeEncBase64;
}
