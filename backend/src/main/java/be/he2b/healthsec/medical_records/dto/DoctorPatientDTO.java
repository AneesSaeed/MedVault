package be.he2b.healthsec.medical_records.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Doctor view of an appointed patient.
 *
 * <p>Contains encrypted patient identity fields and the patient's AES key wrapped for the doctor.
 * The server returns these values as-is; decryption is client-side.</p>
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
public class DoctorPatientDTO {
    
    /** Patient identifier */
    private String patientId;
    
    /** Encrypted patient first name*/
    private String firstNameEnc;
    
    /** Encrypted patient last name*/
    private String lastNameEnc;
    
    /** Encrypted patient email*/
    private String emailEnc;
    
    /** Patient AES key wrapped for the doctor */
    private String encryptedAESKey;
}
