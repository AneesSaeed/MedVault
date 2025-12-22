package be.he2b.healthsec.medical_records.model;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class MedicalFileKeyId implements Serializable {
    private UUID fileId;
    private UUID recipientUserId;
}
