package be.he2b.healthsec.medical_records.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Doctor -> patient file request payload.
 *
 * <p>All fields are Base64-encoded. Encrypted fields remain encrypted end-to-end
 * (server stores/forwards them and does not decrypt).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePendingMedicalFileDTO {
    @NotBlank(message = "fileNameEncBase64 is required")
    private String fileNameEncBase64;

    @NotBlank(message = "contentEncBase64 is required")
    private String contentEncBase64;

     /** IV/nonce used for encryption, Base64-encoded. */
    @NotBlank(message = "ivBase64 is required")
    private String ivBase64;

     /** Temporary symmetric key wrapped for the patient, Base64-encoded. */
    @NotBlank(message = "wrappedTempKeyForPatientBase64 is required")
    private String wrappedTempKeyForPatientBase64;

    /** Encrypted MIME type, Base64-encoded. */
    @NotBlank(message = "mimeTypeEncBase64 is required")
    private String mimeTypeEncBase64;
}
