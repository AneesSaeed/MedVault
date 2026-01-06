package be.he2b.healthsec.medical_records.model;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite key for {@link MedicalFileKey}: (fileId, recipientUserId).
 */
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class MedicalFileKeyId implements Serializable {
    private UUID fileId;
    private UUID recipientUserId;
}
