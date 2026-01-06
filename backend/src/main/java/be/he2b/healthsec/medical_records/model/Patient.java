package be.he2b.healthsec.medical_records.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Patient profile linked 1:1 to {@link User} (shared primary key).
 *
 * <p>Patient identity fields are stored encrypted (bytea) to prevent enumeration via DB access.
 * Decryption is client-side using the patient's AES key.</p>
 */
@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @MapsId
    @JoinColumn(name = "id")
    private User user;
    
    @Column(name = "first_name_enc", nullable = false, columnDefinition = "bytea")
    private byte[] firstNameEnc;

    @Column(name = "last_name_enc", nullable = false, columnDefinition = "bytea")
    private byte[] lastNameEnc;

    @Column(name = "email_enc", nullable = false, columnDefinition = "bytea")
    private byte[] emailEnc;
    
    @Column(name = "dob_enc", nullable = false, columnDefinition = "bytea")
    private byte[] dateOfBirthEnc;

    @Builder.Default
    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PatientDoctor> doctorLinks = new HashSet<>();

    @OneToOne(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    private MedicalRecord medicalRecord;
}
