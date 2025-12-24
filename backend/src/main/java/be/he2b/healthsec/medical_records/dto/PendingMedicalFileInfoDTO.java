package be.he2b.healthsec.medical_records.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * DTO pour exposer une demande en attente au patient.
 */
@Data
@Builder
public class PendingMedicalFileInfoDTO {
    private String id;
    private String uploaderDoctorId;
    private String fileNameEncBase64; // optionnel
    private String contentEncBase64;
    private String ivBase64;
    private String wrappedTempKeyForPatientBase64;
    private String mimeTypeEncBase64; // optionnel
    private Instant createdAt;
}
