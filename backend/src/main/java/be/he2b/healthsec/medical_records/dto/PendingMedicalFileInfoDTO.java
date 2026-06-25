package be.he2b.healthsec.medical_records.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Pending medical file request exposed to the patient.
 *
 * <p>Contains encrypted payload (Base64) prepared by a doctor. The patient can decrypt client-side
 * using the wrapped temporary key.</p>
 */
@Data
@Builder
public class PendingMedicalFileInfoDTO {
    private String id;
    private String uploaderDoctorId;
    private String fileNameEncBase64;
    private String contentEncBase64;
    private String ivBase64;
    private String wrappedTempKeyForPatientBase64;
    private String mimeTypeEncBase64;
    private Instant createdAt;
}
