package be.he2b.healthsec.medical_records.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Encrypted patient data returned to an authorized requester (patient or appointed doctor).
 *
 * <p>All personal fields are encrypted with the patient's AES key and Base64-encoded.
 * The AES key is wrapped for the requester using the requester's RSA public key and Base64-encoded.
 * Decryption is client-side.</p>
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class PatientDataDTO {
    
    private String patientId;
    private String firstNameEncBase64;
    private String lastNameEncBase64;
    private String emailEncBase64;
    private String dateOfBirthEncBase64;
    
    /** Patient AES key wrapped for the requester */
    private String symmetricKeyEncBase64;
}
