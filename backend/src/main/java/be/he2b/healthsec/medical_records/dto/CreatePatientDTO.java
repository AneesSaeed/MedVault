package be.he2b.healthsec.medical_records.dto;

import be.he2b.healthsec.medical_records.model.User;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreatePatientDTO {
    private User user;
    private String dateOfBirthEncBase64;
}
