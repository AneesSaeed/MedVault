package be.he2b.healthsec.medical_records.repository;

import be.he2b.healthsec.medical_records.model.PatientDoctor;
import be.he2b.healthsec.medical_records.model.PatientDoctorId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PatientDoctorRepository extends JpaRepository<PatientDoctor, PatientDoctorId> {
    List<PatientDoctor> findByPatientId(UUID patientId);
    List<PatientDoctor> findByDoctorId(UUID doctorId);
}
