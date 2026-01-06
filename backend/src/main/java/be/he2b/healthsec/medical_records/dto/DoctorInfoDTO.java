package be.he2b.healthsec.medical_records.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Doctor metadata returned to patients for identification/search.
 *
 * <p>Fields are intentionally cleartext to allow discovery, plus the doctor's RSA public key
 * used by patients to wrap shared keys.</p>
 */
@Getter @Setter
public class DoctorInfoDTO {
    /** Doctor identifier */
    private String doctorId;
    private String firstName; 
    private String lastName;
    private String medicalOrganization;
    /** Doctor RSA public key */
    private String publicKeyPEM;
}

