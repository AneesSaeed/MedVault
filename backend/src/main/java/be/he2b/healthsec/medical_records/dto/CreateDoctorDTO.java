package be.he2b.healthsec.medical_records.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Doctor onboarding payload.
 *
 * <p>Doctor identity fields are stored in cleartext so patients can search and identify doctors.
 * The public RSA key is used to wrap patient symmetric keys when a doctor is appointed.</p>
 */
@Getter @Setter
public class CreateDoctorDTO {
    
    /** Doctor first name (cleartext). */
    @NotBlank(message = "firstName is required")
    private String firstName;
    
    /** Doctor last name (cleartext). */
    @NotBlank(message = "lastName is required")
    private String lastName;
    
    /** Doctor email address (cleartext). */
    @Email(message = "email must be valid")
    @NotBlank(message = "email is required")
    private String email;
    
    /** Doctor medical organization (cleartext). */
    @NotBlank(message = "medicalOrganization is required")
    private String medicalOrganization;
    
    /** Doctor RSA public key (PEM). Used to wrap patient AES keys for sharing. */
    @NotBlank(message = "publicKeyPEM is required")
    private String publicKeyPEM;
}