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
 * Demande d'ajout de fichier médical par un docteur à un patient.
 * Le contenu est chiffré côté docteur avec une clé AES temporaire, et la clé
 * temporaire est "wrap" avec la clé publique RSA du patient. Le patient
 * déchiffre côté client et décide d'accepter ou rejeter.
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

    /**
     * Nom du fichier chiffré (optionnel)
     */
    @Column(name = "file_name_enc")
    private byte[] fileNameEnc;

    /**
     * Contenu chiffré avec AES-GCM (clé temporaire Ktemp)
     */
    @Column(name = "content_enc", nullable = false)
    private byte[] contentEnc;

    /**
     * IV utilisé pour AES-GCM
     */
    @Column(name = "iv", nullable = false)
    private byte[] iv;

    /**
     * Clé AES temporaire Ktemp "wrap" avec RSA-OAEP utilisant la clé publique du patient
     */
    @Column(name = "wrapped_temp_key_for_patient", nullable = false)
    private byte[] wrappedTempKeyForPatient;

    /**
     * Type MIME chiffré (optionnel)
     */
    @Column(name = "mime_type_enc")
    private byte[] mimeTypeEnc;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
