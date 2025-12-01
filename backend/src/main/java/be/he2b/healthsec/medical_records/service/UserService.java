package be.he2b.healthsec.medical_records.service;

import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Service;

import be.he2b.healthsec.medical_records.model.Doctor;
import be.he2b.healthsec.medical_records.model.Patient;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.repository.DoctorRepository;
import be.he2b.healthsec.medical_records.repository.PatientRepository;
import be.he2b.healthsec.medical_records.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    public String createDoctor(User user, String medicalOrganizationEncBase64) {
        user.setCreatedAt(Instant.now());
        User savedUser = userRepository.save(user);

        byte[] medicalOrganizationEnc = Base64.getDecoder().decode(medicalOrganizationEncBase64);

        Doctor doctor = Doctor.builder()
                .user(savedUser)
                .medicalOrganizationEnc(medicalOrganizationEnc)
                .build();

        doctorRepository.save(doctor);

        return "Doctor created with ID: " + savedUser.getId();
    }

    public String createPatient(User user, String dateOfBirthEncBase64) {
        user.setCreatedAt(Instant.now());
        User savedUser = userRepository.save(user);

        byte[] dateOfBirthEnc = Base64.getDecoder().decode(dateOfBirthEncBase64);

        Patient patient = Patient.builder()
                .user(savedUser)
                .dateOfBirthEnc(dateOfBirthEnc)
                .build();

        patientRepository.save(patient);
        return "Patient created with ID: " + savedUser.getId();
    }
}
