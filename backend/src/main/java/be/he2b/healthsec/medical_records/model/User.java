package be.he2b.healthsec.medical_records.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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

    /**
     * Prénom en clair.
     * Pour les patients et les médecins, le prénom est stocké en clair
     * pour permettre la recherche et l'identification.
     */
    @Column(name = "first_name", nullable = false)
    private String firstName;

    /**
     * Nom en clair.
     * Pour les patients et les médecins, le nom est stocké en clair
     * pour permettre la recherche et l'identification.
     */
    @Column(name = "last_name", nullable = false)
    private String lastName;

    /**
     * Email chiffré avec AES.
     * L'email reste chiffré pour protéger la confidentialité.
     * Nullable pour les médecins (pas nécessaire car leurs données sont en clair).
     */
    @Column(name = "email_enc", columnDefinition = "bytea")
    private byte[] emailEnc;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type_role", nullable = false)
    private UserType role; // DOCTOR ou PATIENT

    @Column(name = "public_key")
    private String publicKey; // clé publique PEM/base64

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
