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
    
    /**
     * SÉCURITÉ: Prénom du patient chiffré avec sa clé AES.
     * Les prénoms des patients sont chiffrés pour empêcher l'énumération
     * via accès direct à la base de données.
     * User.firstName peut être laissé NULL ou contenir une valeur générique.
     */
    @Column(name = "first_name_enc", nullable = false, columnDefinition = "bytea")
    private byte[] firstNameEnc;
    
    /**
     * SÉCURITÉ: Nom du patient chiffré avec sa clé AES.
     * Les noms des patients sont chiffrés pour empêcher l'énumération
     * via accès direct à la base de données.
     * User.lastName peut être laissé NULL ou contenir une valeur générique.
     */
    @Column(name = "last_name_enc", nullable = false, columnDefinition = "bytea")
    private byte[] lastNameEnc;
    
    /**
     * SÉCURITÉ: Email du patient chiffré avec sa clé AES.
     * User.email doit être laissé NULL pour les patients.
     */
    @Column(name = "email_enc", nullable = false, columnDefinition = "bytea")
    private byte[] emailEnc;
    
    /**
     * Date de naissance chiffrée avec la clé AES du patient.
     */
    @Column(name = "dob_enc", nullable = false, columnDefinition = "bytea")
    private byte[] dateOfBirthEnc;
        //important à bien comprendre

    /**
     * NOTE: La clé symétrique AES du patient n'est PAS stockée ici.
     * Elle est stockée uniquement dans PatientDoctor.encryptedSymmetricKeyForDoctor
     * quand un médecin est autorisé, chiffrée avec la clé publique RSA du médecin.
     * Le patient garde sa clé AES dans le localStorage côté client.
     * Si le patient veut accéder à ses données depuis un autre appareil,
     * il doit utiliser sa clé privée RSA pour déchiffrer la clé AES stockée
     * dans PatientDoctor (ou implémenter un système de récupération sécurisé).
     */

    // Un patient a plusieurs liens PatientDoctor
    @Builder.Default
    @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PatientDoctor> doctorLinks = new HashSet<>();

    @OneToOne(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
    private MedicalRecord medicalRecord;
}
