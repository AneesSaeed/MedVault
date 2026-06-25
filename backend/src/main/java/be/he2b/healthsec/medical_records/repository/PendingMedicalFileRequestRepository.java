package be.he2b.healthsec.medical_records.repository;

import be.he2b.healthsec.medical_records.model.PendingMedicalFileRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PendingMedicalFileRequestRepository extends JpaRepository<PendingMedicalFileRequest, UUID> {

    @Query("SELECT r FROM PendingMedicalFileRequest r WHERE r.patient.id = :patientId ORDER BY r.createdAt DESC")
    List<PendingMedicalFileRequest> findByPatientId(@Param("patientId") UUID patientId);
}
