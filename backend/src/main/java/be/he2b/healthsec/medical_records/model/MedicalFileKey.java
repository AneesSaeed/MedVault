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

@Entity
@Table(name = "medical_file_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalFileKey {

    /**
     * Composite primary key (e.g., fileId + recipientUserId).
     * Must be an @Embeddable class.
     */
    @EmbeddedId
    private MedicalFileKeyId id;

    /**
     * FK to MedicalFile, and also part of the composite PK via @MapsId("fileId").
     * Column in DB: file_id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("fileId")
    @JoinColumn(name = "file_id", nullable = false)
    private MedicalFile file;

    /**
     * FK to User (recipient), also part of the composite PK via @MapsId("recipientUserId").
     * Column in DB: recipient_user_id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("recipientUserId")
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    /**
     * Symmetric file key (K_file) encrypted/wrapped with the recipient's RSA public key.
     * Stored as bytes in Postgres bytea.
     */
    @Column(name = "wrapped_file_key_enc", columnDefinition = "bytea", nullable = false)
    private byte[] wrappedFileKeyEnc;
}
