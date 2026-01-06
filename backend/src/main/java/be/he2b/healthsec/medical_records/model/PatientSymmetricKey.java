package be.he2b.healthsec.medical_records.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores the patient's AES key wrapped for each recipient.
 *
 * <p>Pattern: one patient AES key, multiple wrapped copies (per recipient RSA public key).
 * Used to decrypt patient identity fields client-side; server never decrypts.</p>
 */
@Entity
@Table(name = "patient_symmetric_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientSymmetricKey {

    @EmbeddedId
    private PatientSymmetricKeyId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("patientId")
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("recipientUserId")
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @Column(name = "wrapped_symmetric_key_enc", nullable = false, columnDefinition = "bytea")
    private byte[] wrappedSymmetricKeyEnc;
}
