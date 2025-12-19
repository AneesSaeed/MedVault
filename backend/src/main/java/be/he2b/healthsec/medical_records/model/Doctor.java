package be.he2b.healthsec.medical_records.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "doctors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Doctor {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    /**
     * SÉCURITÉ: Toutes les informations des médecins sont stockées EN CLAIR
     * car les médecins sont "découvrables" par les patients pour l'identification.
     */
    
    /**
     * Prénom du médecin (en clair).
     * Permet aux patients de rechercher et identifier les médecins.
     */
    @Column(name = "first_name", nullable = false)
    private String firstName;
    
    /**
     * Nom du médecin (en clair).
     * Permet aux patients de rechercher et identifier les médecins.
     */
    @Column(name = "last_name", nullable = false)
    private String lastName;
    
    /**
     * Email du médecin (en clair).
     * Contact professionnel visible par les patients.
     */
    @Column(name = "email", nullable = false)
    private String email;

    /**
     * Organisation médicale en clair.
     * Les informations des médecins sont stockées en clair pour permettre
     * aux patients de les identifier et de les rechercher.
     */
    @Column(name = "medical_organisation", nullable = false)
    private String medicalOrganization;

    // Un doctor a plusieurs liens PatientDoctor
    @Builder.Default
    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PatientDoctor> patientLinks = new HashSet<>();
}
