package be.he2b.healthsec.medical_records.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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


/**
 * Base account entity linked to a Keycloak subject.
 *
 * <p>Stores the Keycloak user id and the user's RSA public key (PEM). Domain-specific profiles
 * are modeled via {@link Patient} and {@link Doctor} which share the same UUID as this user.</p>
 */
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
    private String keycloakId;

    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
