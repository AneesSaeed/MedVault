package be.he2b.healthsec.medical_records.service;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Service;

import be.he2b.healthsec.medical_records.model.Doctor;
import be.he2b.healthsec.medical_records.model.Patient;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.model.UserType;
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

    
    public String createPatient(String keycloakId, 
                                String firstNameEncBase64, 
                                String lastNameEncBase64,
                                String emailEncBase64,
                                String dateOfBirthEncBase64, 
                                String publicKeyPEM) {
        
        // Prevent duplicates
        Optional<User> existing = userRepository.findByKeycloakId(keycloakId);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }

        // SÉCURITÉ: User contient uniquement les champs communs
        // Les données personnelles du patient sont stockées chiffrées dans Patient
        User user = User.builder()
            .keycloakId(keycloakId)
            .role(UserType.PATIENT)
            .publicKey(publicKeyPEM)
            .createdAt(Instant.now())
            .build();
        
        User savedUser = userRepository.save(user);
        
        // Décodage des données chiffrées
        byte[] firstNameEnc = Base64.getDecoder().decode(firstNameEncBase64);
        byte[] lastNameEnc = Base64.getDecoder().decode(lastNameEncBase64);
        byte[] emailEnc = Base64.getDecoder().decode(emailEncBase64);
        byte[] dateOfBirthEnc = Base64.getDecoder().decode(dateOfBirthEncBase64);
        
        // NOTE: La clé AES du patient n'est PAS stockée ici.
        // Elle reste dans le localStorage côté client.
        // Elle sera partagée avec les médecins uniquement lors de la création
        // de la relation PatientDoctor, chiffrée avec la clé publique RSA du médecin.
        Patient patient = Patient.builder()
            .user(savedUser)
            .firstNameEnc(firstNameEnc)
            .lastNameEnc(lastNameEnc)
            .emailEnc(emailEnc)
            .dateOfBirthEnc(dateOfBirthEnc)
            .build();
        
        patientRepository.save(patient);
        return "Patient created with ID: " + savedUser.getId();
    }
    
    public String createDoctor(String keycloakId, 
                               String firstName, 
                               String lastName, 
                               String email,
                               String medicalOrganization, 
                               String publicKeyPEM) {
        
        // Prevent duplicates
        Optional<User> existing = userRepository.findByKeycloakId(keycloakId);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }

        // Pour les médecins, User contient seulement les champs communs
        User user = User.builder()
                .keycloakId(keycloakId)
                .role(UserType.DOCTOR)
                .publicKey(publicKeyPEM)
                .createdAt(Instant.now())
                .build();
        
        User savedUser = userRepository.save(user);
    
        // Les données personnelles du médecin sont stockées EN CLAIR dans Doctor
        Doctor doctor = Doctor.builder()
                .user(savedUser)
                .firstName(firstName)              // EN CLAIR
                .lastName(lastName)                // EN CLAIR
                .email(email)                      // EN CLAIR
                .medicalOrganization(medicalOrganization)  // EN CLAIR
                .build();
    
        doctorRepository.save(doctor);
    
        return "Doctor created with ID: " + savedUser.getId();
    }
}
