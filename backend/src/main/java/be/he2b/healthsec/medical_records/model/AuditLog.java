package be.he2b.healthsec.medical_records.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private UUID userId; // id du User qui a fait l’action

    @Column(nullable = false)
    private String action; // ex: UPLOAD_FILE, DELETE_FILE, etc.

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = true)
    private String details; // infos additionnelles (ex: nom fichier)
}
