package be.he2b.healthsec.medical_records.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * "Me" endpoint response.
 *
 * <p>Doctors receive cleartext identity fields. Patients receive encrypted identity fields plus
 * their AES key wrapped for themselves (Base64).</p>
 */
@Getter
@Builder
public class MeResponseDTO {
    private String userId;

    // doctor cleartext
    private String firstName;
    private String lastName;

    // patient encrypted
    private String firstNameEncBase64;
    private String lastNameEncBase64;

    // AES key wrapped for requesting user
    private String symmetricKeyEncBase64;
}
