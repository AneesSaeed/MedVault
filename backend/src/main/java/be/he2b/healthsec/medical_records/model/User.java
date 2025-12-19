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
     * ARCHITECTURE SIMPLIFIÉE:
     * - User contient UNIQUEMENT les champs communs (id, keycloakId, role, publicKey)
     * - Les données personnelles sont dans Patient (chiffrées) ou Doctor (en clair)
     * - Pas de firstName/lastName/email ici pour éviter la confusion
     */

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type_role", nullable = false)
    private UserType role; // DOCTOR ou PATIENT

    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey; // clé publique PEM/base64 (peut être très longue, ~450+ caractères)

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
