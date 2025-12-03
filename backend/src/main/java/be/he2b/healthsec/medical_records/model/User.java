package be.he2b.healthsec.medical_records.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "keycloak_id", unique = true, nullable = false)
    private String keycloakId; // id fourni par Keycloak

    @Column(name = "first_name_enc", columnDefinition = "bytea")
    private byte[] firstNameEnc;

    @Column(name = "last_name_enc", columnDefinition = "bytea")
    private byte[] lastNameEnc;

    @Column(name = "email_enc", columnDefinition = "bytea")
    private byte[] emailEnc;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type_role", nullable = false)
    private UserType role; // DOCTOR ou PATIENT

    @Column(name = "user_type_enc", columnDefinition = "bytea")
    private byte[] userTypeEnc;

    @Column(name = "public_key")
    private String publicKey; // clé publique PEM/base64

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
