package be.he2b.healthsec.medical_records.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Request payload used by a patient to appoint a doctor.
 *
 * <p>Contains the doctor identifier and the patient's symmetric key wrapped for that doctor
 * (encrypted with the doctor's RSA public key and base64-encoded). The backend stores/transmits
 * this value and does not decrypt it.</p>
 */
@Getter @Setter
public class AddDoctorToPatientDTO {
    /** Doctor identifier to appoint. */
    @NotBlank(message = "doctorId is required")
    private String doctorId;
    
    /** Patient AES key encrypted for the doctor (RSA-wrapped), base64-encoded. */
    @NotBlank(message = "encryptedPatientAESKeyBase64 is required")
    private String encryptedPatientAESKeyBase64;
}

