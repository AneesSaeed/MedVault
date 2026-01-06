package be.he2b.healthsec.medical_records.dto;
import lombok.Data;

/**
 * Metadata returned when listing medical files.
 *
 * <p>File name/date remain encrypted (Base64). The wrapped key is specific to the current requester
 * (patient or appointed doctor) and is used client-side to decrypt the file.</p>
 */
@Data
public class MedicalFileInfoDTO {
    private String id;
    private String fileNameEncBase64;
    private String uploadDateEncBase64;
    private long sizeBytes;
    private String wrappedFileKeyEncBase64;
}
