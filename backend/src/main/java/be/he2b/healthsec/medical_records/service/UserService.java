package be.he2b.healthsec.medical_records.service;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Service;

import be.he2b.healthsec.medical_records.model.Doctor;
import be.he2b.healthsec.medical_records.model.Patient;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.repository.DoctorRepository;
import be.he2b.healthsec.medical_records.repository.PatientRepository;
import be.he2b.healthsec.medical_records.repository.UserRepository;
import lombok.RequiredArgsConstructor;
    //important à bien comprendre

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    public boolean existsByKeycloakId(String keycloakId) {
        return userRepository.existsByKeycloakId(keycloakId);
    }

    public Optional<User> findByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId);
    }

    
    public String createPatient(User user, String dateOfBirthEncBase64, 
                                String publicKeyPEM) {
        
        // Prevent duplicates
        Optional<User> existing = userRepository.findByKeycloakId(user.getKeycloakId());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }

        // Stocke la clé publique RSA
        user.setPublicKey(publicKeyPEM);
        user.setCreatedAt(Instant.now());
        User savedUser = userRepository.save(user);
        
        byte[] dateOfBirthEnc = Base64.getDecoder().decode(dateOfBirthEncBase64);
        
        // NOTE: La clé AES du patient n'est PAS stockée ici.
        // Elle reste dans le localStorage côté client.
        // Elle sera partagée avec les médecins uniquement lors de la création
        // de la relation PatientDoctor, chiffrée avec la clé publique RSA du médecin.
        Patient patient = Patient.builder()
            .user(savedUser)
            .dateOfBirthEnc(dateOfBirthEnc)
            .build();
        
        patientRepository.save(patient);
        return "Patient created with ID: " + savedUser.getId();
    }
    
    public String createDoctor(User user, String medicalOrganization, String publicKeyPEM) {
        
        // Prevent duplicates
        Optional<User> existing = userRepository.findByKeycloakId(user.getKeycloakId());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }

        // Stocke la clé publique RSA
        user.setPublicKey(publicKeyPEM);
        user.setCreatedAt(Instant.now());
        User savedUser = userRepository.save(user);
    
        // Organisation médicale en clair (pas de chiffrement nécessaire)
        Doctor doctor = Doctor.builder()
                .user(savedUser)
                .medicalOrganization(medicalOrganization)
                .build();
    
        doctorRepository.save(doctor);
    
        return "Doctor created with ID: " + savedUser.getId();
    }
}
