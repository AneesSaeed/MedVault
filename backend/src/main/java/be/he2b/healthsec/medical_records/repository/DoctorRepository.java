package be.he2b.healthsec.medical_records.repository;

import be.he2b.healthsec.medical_records.model.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {
}
