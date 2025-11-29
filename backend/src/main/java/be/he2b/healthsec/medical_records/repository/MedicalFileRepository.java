package be.he2b.healthsec.medical_records.repository;

import be.he2b.healthsec.medical_records.model.MedicalFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface MedicalFileRepository extends JpaRepository<MedicalFile, UUID> {
    List<MedicalFile> findByMedicalRecordId(UUID medicalRecordId);
}
