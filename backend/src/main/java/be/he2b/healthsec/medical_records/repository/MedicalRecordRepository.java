package be.he2b.healthsec.medical_records.repository;

import be.he2b.healthsec.medical_records.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {
}
