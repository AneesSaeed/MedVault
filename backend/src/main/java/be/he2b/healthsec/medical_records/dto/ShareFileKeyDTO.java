package be.he2b.healthsec.medical_records.dto;

import lombok.Data;

@Data
public class ShareFileKeyDTO {
    private String fileId;
    private String wrappedKeyForDoctorBase64;
}