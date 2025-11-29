package be.he2b.healthsec.medical_records.repository;

import be.he2b.healthsec.medical_records.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByUserId(UUID userId);
}
