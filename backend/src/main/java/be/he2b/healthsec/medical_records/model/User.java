package be.he2b.healthsec.medical_records.model;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import jakarta.persistence.*;
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

    @Lob
    @Column(name = "first_name_enc")
    private byte[] firstNameEnc;

    @Lob
    @Column(name = "last_name_enc")
    private byte[] lastNameEnc;

    @Lob
    @Column(name = "email_enc")
    private byte[] emailEnc;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type_role", nullable = false)
    private UserType role; // DOCTOR ou PATIENT

    @Lob
    @Column(name = "user_type_enc")
    private byte[] userTypeEnc;

    @Lob
    @Column(name = "public_key")
    private String publicKey; // clé publique PEM/base64

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
