package be.he2b.healthsec.medical_records.model;

import lombok.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
    private UUID id; // même id que User

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    @Lob
    @Column(name = "dob_enc", nullable = false)
    private byte[] dateOfBirthEnc;


    // Un patient a plusieurs liens PatientDoctor
    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PatientDoctor> doctorLinks = new HashSet<>();

    
    @OneToOne(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    private MedicalRecord medicalRecord;
}
