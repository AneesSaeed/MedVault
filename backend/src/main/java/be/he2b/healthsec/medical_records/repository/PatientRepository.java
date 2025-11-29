package be.he2b.healthsec.medical_records.repository;

import be.he2b.healthsec.medical_records.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
}
