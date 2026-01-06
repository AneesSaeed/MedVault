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
 * Stores a per-recipient wrapped file key for a medical file.
 *
 * <p>The file is encrypted client-side with a symmetric key (K_file). This table stores K_file
 * wrapped for each authorized recipient (patient / appointed doctor) using their RSA public key.</p>
 */
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
     */
    @EmbeddedId
    private MedicalFileKeyId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("fileId")
    @JoinColumn(name = "file_id", nullable = false)
    private MedicalFile file;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("recipientUserId")
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @Column(name = "wrapped_file_key_enc", columnDefinition = "bytea", nullable = false)
    private byte[] wrappedFileKeyEnc;
}
