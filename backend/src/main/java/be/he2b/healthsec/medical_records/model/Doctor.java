package be.he2b.healthsec.medical_records.model;

import lombok.*;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "doctors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Doctor {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id; // même id que User

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    @Lob
    @Column(name = "medical_organisation_enc")
    private byte[] medicalOrganizationEnc;


    // Un doctor a plusieurs liens PatientDoctor
    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PatientDoctor> patientLinks = new HashSet<>();
}
