package be.he2b.healthsec.medical_records.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Doctor -> patient pending request containing an encrypted file payload.
 *
 * <p>The doctor encrypts content client-side with a temporary AES key; that key is wrapped for the
 * patient using the patient's RSA public key. The server stores and forwards ciphertext only.</p>
 */
@Entity
@Table(name = "pending_medical_file_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingMedicalFileRequest {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_doctor_id", nullable = false)
    private Doctor uploaderDoctor;

    @Column(name = "file_name_enc")
    private byte[] fileNameEnc;

    @Column(name = "content_enc", nullable = false)
    private byte[] contentEnc;

    @Column(name = "iv", nullable = false)
    private byte[] iv;

    @Column(name = "wrapped_temp_key_for_patient", nullable = false)
    private byte[] wrappedTempKeyForPatient;

    @Column(name = "mime_type_enc")
    private byte[] mimeTypeEnc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
