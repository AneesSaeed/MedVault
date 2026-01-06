package be.he2b.healthsec.medical_records.dto;

import lombok.Data;

/**
 * Payload used by a patient to share a file key with a doctor.
 *
 * <p>The file key is wrapped (encrypted) for the doctor client-side and sent as Base64.</p>
 */
@Data
public class ShareFileKeyDTO {
    private String fileId;
    private String wrappedKeyForDoctorBase64;
}