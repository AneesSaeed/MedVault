package be.he2b.healthsec.medical_records.model;

import java.time.Instant;

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
@Table(name = "patient_doctor")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PatientDoctor {

    @EmbeddedId
    private PatientDoctorId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("patientId")
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("doctorId")
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    @Column(name = "approved_by_patient", nullable = false)
    private boolean approvedByPatient;

    @Column(name = "appointed_at", nullable = true)
    private Instant appointedAt;

    /** 
     * Clé symétrique AES du PATIENT chiffrée avec la clé publique RSA du docteur.
     * 
     * Cette clé permet au médecin de déchiffrer les dossiers médicaux du patient.
     * 
     * SÉCURITÉ:
     * - Cette clé est créée uniquement lors de la création de la relation PatientDoctor
     * - Elle est chiffrée avec la clé publique RSA du médecin
     * - Seul le médecin (avec sa clé privée RSA) peut la déchiffrer
     * - Chaque relation PatientDoctor a sa propre copie de la clé AES du patient
     * - Si la relation est supprimée, cette clé est également supprimée
     */
    @Column(name = "encrypted_sym_key_for_doctor", columnDefinition = "bytea")
    private byte[] encryptedSymmetricKeyForDoctor;

}


