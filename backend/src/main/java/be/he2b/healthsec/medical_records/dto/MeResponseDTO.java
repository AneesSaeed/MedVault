package be.he2b.healthsec.medical_records.dto;

import lombok.Builder;
import lombok.Getter;

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

    // patient: AES key wrapped for requesting user (patient himself here)
    private String symmetricKeyEncBase64;
}
