package be.he2b.healthsec.medical_records.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Patient onboarding payload.
 *
 * <p>All patient personal data is encrypted client-side with the patient's AES key.
 * The server never stores patient identity data in cleartext.</p>
 *
 * <p>The patient's AES key is itself wrapped (encrypted) with the patient's RSA public key
 * so it can later be shared (still encrypted) with appointed doctors.</p>
 */
@Getter @Setter
public class CreatePatientDTO {
    
    /** Patient first name encrypted with patient AES key, Base64-encoded. */
    @NotBlank(message = "firstNameEncBase64 is required")
    private String firstNameEncBase64;
    
    /** Patient last name encrypted with patient AES key, Base64-encoded. */
    @NotBlank(message = "lastNameEncBase64 is required")
    private String lastNameEncBase64;
    
    /** Patient email encrypted with patient AES key, Base64-encoded. */
    @NotBlank(message = "emailEncBase64 is required")
    private String emailEncBase64;
    
     /** Patient date of birth encrypted with patient AES key, Base64-encoded. */
    @NotBlank(message = "dateOfBirthEncBase64 is required")
    private String dateOfBirthEncBase64;

    /** Patient RSA public key (PEM). Used for key wrapping and sharing workflows. */
    @NotBlank(message = "publicKeyPEM is required")
    private String publicKeyPEM;

    /** Patient AES key wrapped with the patient's RSA public key*/
    @NotBlank(message = "symmetricKeyEncBase64 is required")
    private String symmetricKeyEncBase64;
}
