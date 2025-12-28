package be.he2b.healthsec.medical_records.service;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Service;

import be.he2b.healthsec.medical_records.model.Doctor;
import be.he2b.healthsec.medical_records.model.Patient;
import be.he2b.healthsec.medical_records.model.PatientSymmetricKey;
import be.he2b.healthsec.medical_records.model.PatientSymmetricKeyId;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.dto.PatientDataDTO;
import be.he2b.healthsec.medical_records.repository.DoctorRepository;
import be.he2b.healthsec.medical_records.repository.PatientRepository;
import be.he2b.healthsec.medical_records.repository.PatientSymmetricKeyRepository;
import be.he2b.healthsec.medical_records.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PatientSymmetricKeyRepository patientSymmetricKeyRepository;

    public boolean existsByKeycloakId(String keycloakId) {
        return userRepository.existsByKeycloakId(keycloakId);
    }

    public Optional<User> findByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId);
    }

    @Transactional
    public String createPatient(String keycloakId, 
                                String firstNameEncBase64, 
                                String lastNameEncBase64,
                                String emailEncBase64,
                                String dateOfBirthEncBase64, 
                                String publicKeyPEM,
                                String symmetricKeyEncBase64) {
        
        // Prevent duplicates
        Optional<User> existing = userRepository.findByKeycloakId(keycloakId);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("User already exists");
        }

        // SÉCURITÉ: User contient uniquement les champs communs
        // Les données personnelles du patient sont stockées chiffrées dans Patient
        User user = User.builder()
            .keycloakId(keycloakId)
            .publicKey(publicKeyPEM)
            .createdAt(Instant.now())
            .build();
        
        User savedUser = userRepository.save(user);
        
        // Décodage des données chiffrées
        byte[] firstNameEnc = Base64.getDecoder().decode(firstNameEncBase64);
        byte[] lastNameEnc = Base64.getDecoder().decode(lastNameEncBase64);
        byte[] emailEnc = Base64.getDecoder().decode(emailEncBase64);
        byte[] dateOfBirthEnc = Base64.getDecoder().decode(dateOfBirthEncBase64);
        
        Patient patient = Patient.builder()
            .user(savedUser)
            .firstNameEnc(firstNameEnc)
            .lastNameEnc(lastNameEnc)
            .emailEnc(emailEnc)
            .dateOfBirthEnc(dateOfBirthEnc)
            .build();
        
        Patient savedPatient = patientRepository.save(patient);

        // NOUVEAU: Créer une entrée PatientSymmetricKey pour le patient lui-même
        // Cela permet au patient de récupérer sa clé symétrique chiffrée depuis la DB
        // au lieu de la garder dans localStorage
        byte[] symmetricKeyEnc = Base64.getDecoder().decode(symmetricKeyEncBase64);
        
        PatientSymmetricKey patientOwnKey = PatientSymmetricKey.builder()
            .id(new PatientSymmetricKeyId(savedPatient.getId(), savedUser.getId()))
            .patient(savedPatient)
            .recipientUser(savedUser)
            .wrappedSymmetricKeyEnc(symmetricKeyEnc)
            .build();
        
        patientSymmetricKeyRepository.save(patientOwnKey);
        
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

    /**
     * Récupère les données d'un patient avec sa clé symétrique chiffrée.
     * 
     * Utilisé par:
     * - Un patient pour consulter ses propres données
     * - Un docteur pour consulter les données d'un patient (auquel il a accès)
     * 
     * @param patientId ID du patient dont récupérer les données
     * @param requestingUserId ID de l'utilisateur qui demande (patient ou docteur)
     * @return PatientDataDTO avec données chiffrées + clé symétrique chiffrée pour l'utilisateur demandant
     * @throws IllegalArgumentException si le patient n'existe pas ou si l'utilisateur n'a pas accès
     */
    public PatientDataDTO getPatientData(java.util.UUID patientId, java.util.UUID requestingUserId) {
        // 1. Récupérer le patient
        Optional<Patient> patientOpt = patientRepository.findById(patientId);
        if (patientOpt.isEmpty()) {
            throw new IllegalArgumentException("Patient not found");
        }
        Patient patient = patientOpt.get();

        // 2. Vérifier que l'utilisateur demandant a accès:
        //    - C'est le patient lui-même, OU
        //    - C'est un docteur autorisé qui a une PatientSymmetricKey
        PatientSymmetricKey symmetricKey = patientSymmetricKeyRepository
            .findByPatientAndRecipient(patientId, requestingUserId);
        
        if (symmetricKey == null) {
            throw new IllegalArgumentException("User does not have access to this patient's data");
        }

        // 3. Construire et retourner le DTO
        return PatientDataDTO.builder()
            .patientId(patientId.toString())
            .firstNameEncBase64(Base64.getEncoder().encodeToString(patient.getFirstNameEnc()))
            .lastNameEncBase64(Base64.getEncoder().encodeToString(patient.getLastNameEnc()))
            .emailEncBase64(Base64.getEncoder().encodeToString(patient.getEmailEnc()))
            .dateOfBirthEncBase64(Base64.getEncoder().encodeToString(patient.getDateOfBirthEnc()))
            .symmetricKeyEncBase64(Base64.getEncoder().encodeToString(symmetricKey.getWrappedSymmetricKeyEnc()))
            .build();
    }
}
