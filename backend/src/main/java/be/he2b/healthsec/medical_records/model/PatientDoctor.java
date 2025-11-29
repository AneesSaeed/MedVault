package be.he2b.healthsec.medical_records.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
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
     * Clé symétrique chiffrée avec la clé publique du docteur.
     * Cette version est destinée uniquement à ce docteur.
     */
    @Lob
    @Column(name = "encrypted_sym_key_for_doctor")
    private byte[] encryptedSymmetricKeyForDoctor;

}


