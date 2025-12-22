package be.he2b.healthsec.medical_records.dto;
import lombok.Data;

@Data
public class MedicalFileInfoDTO {
    private String id;
    private String fileNameEncBase64;
    private String uploadDateEncBase64;
    private long sizeBytes; // encrypted content length
    private String wrappedFileKeyEncBase64; // wrapped key for the current user
}
