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

/**
 * Appointment link between a patient and a doctor.
 *
 * <p>Optionally stores the patient's AES key wrapped for the doctor so the doctor can decrypt
 * the patient's encrypted data client-side. Removing the link removes access.</p>
 */
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

    @Column(name = "encrypted_sym_key_for_doctor", columnDefinition = "bytea")
    private byte[] encryptedSymmetricKeyForDoctor;
}


