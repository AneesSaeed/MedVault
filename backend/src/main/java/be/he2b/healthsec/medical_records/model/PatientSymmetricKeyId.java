package be.he2b.healthsec.medical_records.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite key: (patientId, recipientUserId) for {@link PatientSymmetricKey}.
 *
 * <p>Identifies the wrapped copy of the patient's AES key for a specific recipient
 * (patient themself or an appointed doctor).</p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PatientSymmetricKeyId implements Serializable {
    
    /**
     * ID du patient propriétaire de la clé symétrique
     */
    @Column(name = "patient_id", columnDefinition = "uuid")
    private UUID patientId;
    
    /**
     * ID du docteur (ou du patient lui-même) qui a accès à la clé symétrique chiffrée
     */
    @Column(name = "recipient_user_id", columnDefinition = "uuid")
    private UUID recipientUserId;
}
