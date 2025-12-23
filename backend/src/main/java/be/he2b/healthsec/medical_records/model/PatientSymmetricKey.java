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
 * Stocke la clé symétrique AES d'un patient, chiffrée pour chaque destinataire.
 * 
 * Architecture:
 * - Patient crée une clé symétrique K_patient
 * - K_patient chiffre TOUTES les données personnelles du patient (firstName, lastName, email, dob)
 * - K_patient est ensuite chiffrée avec la clé publique RSA de chaque destinataire (patient lui-même + docteurs)
 * - Les versions chiffrées sont stockées dans cette table
 * 
 * Analogie: C'est exactement le même pattern que MedicalFileKey, mais pour les données patient au lieu des fichiers.
 */
@Entity
@Table(name = "patient_symmetric_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientSymmetricKey {

    /**
     * Composite primary key (patientId + recipientUserId).
     * Permet de retrouver rapidement la clé chiffrée pour un (patient, docteur) donné.
     */
    @EmbeddedId
    private PatientSymmetricKeyId id;

    /**
     * FK to Patient, et aussi part du composite PK via @MapsId("patientId").
     * Column in DB: patient_id
     * 
     * Représente le patient propriétaire des données (et de la clé symétrique).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("patientId")
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    /**
     * FK to User (destinataire), aussi part du composite PK via @MapsId("recipientUserId").
     * Column in DB: recipient_user_id
     * 
     * Peut être:
     * - Le patient lui-même (pour lui permettre d'accéder à ses données)
     * - Un docteur autorisé
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("recipientUserId")
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    /**
     * K_patient chiffrée avec la clé publique RSA du recipientUser.
     * 
     * Format: RSA-OAEP encrypted AES-256 key, Base64 encoded
     * 
     * Pour déchiffrer:
     * 1. Récupérer wrappedSymmetricKeyEnc
     * 2. Décoder de Base64
     * 3. Déchiffrer avec la clé privée RSA du recipientUser (côté client)
     * 4. Utiliser K_patient en clair pour déchiffrer les données patient (firstName, lastName, email, dob)
     */
    @Column(name = "wrapped_symmetric_key_enc", nullable = false, columnDefinition = "bytea")
    private byte[] wrappedSymmetricKeyEnc;
}
