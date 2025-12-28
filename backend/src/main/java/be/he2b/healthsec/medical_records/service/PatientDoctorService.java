package be.he2b.healthsec.medical_records.service;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import be.he2b.healthsec.medical_records.model.Doctor;
import be.he2b.healthsec.medical_records.model.Patient;
import be.he2b.healthsec.medical_records.model.PatientDoctor;
import be.he2b.healthsec.medical_records.model.PatientDoctorId;
import be.he2b.healthsec.medical_records.model.PatientSymmetricKey;
import be.he2b.healthsec.medical_records.model.PatientSymmetricKeyId;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.dto.DoctorInfoDTO;
import be.he2b.healthsec.medical_records.dto.DoctorPatientDTO;
import be.he2b.healthsec.medical_records.logging.LoggingService;
import be.he2b.healthsec.medical_records.repository.DoctorRepository;
import be.he2b.healthsec.medical_records.repository.MedicalFileKeyRepository;
import be.he2b.healthsec.medical_records.repository.PatientDoctorRepository;
import be.he2b.healthsec.medical_records.repository.PatientRepository;
import be.he2b.healthsec.medical_records.repository.PatientSymmetricKeyRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PatientDoctorService {
    private final PatientDoctorRepository patientDoctorRepository;
    private final MedicalFileKeyRepository medicalFileKeyRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PatientSymmetricKeyRepository patientSymmetricKeyRepository;
    private final LoggingService logger;

    /**
     * Ajoute un médecin à la liste des médecins d'un patient.
     * 
     * Selon l'énoncé : "A patient can add or remove a doctor to his list of appointed doctors."
     * 
     * Cette méthode:
     * 1. Crée la relation PatientDoctor
     * 2. NOUVEAU: Crée aussi une ligne PatientSymmetricKey pour le docteur
     *    (la clé symétrique du patient chiffrée avec la clé publique RSA du docteur)
     * 
     * @param patientId ID du patient
     * @param doctorId ID du médecin
     * @param encryptedPatientAESKeyBase64 Clé AES du patient chiffrée avec la clé publique RSA du médecin
     * @return Message de confirmation
     */
    @Transactional
    public String addDoctorToPatient(UUID patientId, UUID doctorId, 
                                     String encryptedPatientAESKeyBase64) {
        logger.debug("Adding doctor to patient", java.util.Map.of(
            "doctorId", doctorId,
            "patientId", patientId
        ));
        
        // Vérifie que le patient existe
        Optional<Patient> patientOpt = patientRepository.findById(patientId);
        if (patientOpt.isEmpty()) {
            logger.warn("Patient not found", java.util.Map.of(
                "patientId", patientId
            ));
            throw new IllegalArgumentException("Patient not found");
        }
        Patient patient = patientOpt.get();

        // Vérifie que le médecin existe
        Optional<Doctor> doctorOpt = doctorRepository.findById(doctorId);
        if (doctorOpt.isEmpty()) {
            logger.warn("Doctor not found", java.util.Map.of(
                "doctorId", doctorId
            ));
            throw new IllegalArgumentException("Doctor not found");
        }
        Doctor doctor = doctorOpt.get();

        // Vérifie que la relation n'existe pas déjà
        PatientDoctorId id = new PatientDoctorId(patientId, doctorId);
        if (patientDoctorRepository.existsById(id)) {
            logger.warn("Doctor is already associated with patient", java.util.Map.of(
                "doctorId", doctorId,
                "patientId", patientId
            ));
            throw new IllegalArgumentException("Doctor is already associated with this patient");
        }

        // Décode la clé AES chiffrée
        byte[] encryptedPatientAESKey = Base64.getDecoder().decode(encryptedPatientAESKeyBase64);

        // Crée la relation PatientDoctor
        // Note: Les informations du médecin sont en clair, donc pas besoin de les stocker chiffrées
        PatientDoctor patientDoctor = PatientDoctor.builder()
            .id(id)
            .patient(patient)
            .doctor(doctor)
            .approvedByPatient(true) // Le patient ajoute directement, donc approuvé
            .appointedAt(Instant.now())
            .encryptedSymmetricKeyForDoctor(encryptedPatientAESKey)
            .build();

        patientDoctorRepository.save(patientDoctor);

        // NOUVEAU: Créer une entrée PatientSymmetricKey pour le docteur
        // Cela permet au docteur de récupérer la clé symétrique du patient
        // (chiffrée avec sa clé publique) depuis la DB pour déchiffrer les données patient
        PatientSymmetricKey doctorKeyEntry = PatientSymmetricKey.builder()
            .id(new PatientSymmetricKeyId(patientId, doctorId))
            .patient(patient)
            .recipientUser(doctor.getUser())
            .wrappedSymmetricKeyEnc(encryptedPatientAESKey)
            .build();

        patientSymmetricKeyRepository.save(doctorKeyEntry);
        
        logger.info("Doctor successfully added to patient", java.util.Map.of(
            "doctorId", doctorId,
            "patientId", patientId
        ));

        return "Doctor added to patient's list successfully";
    }

    /**
     * Récupère la clé publique RSA d'un médecin.
     * 
     * @param doctorId ID du médecin
     * @return Clé publique RSA en format PEM
     */
    public String getDoctorPublicKey(UUID doctorId) {
        logger.debug("Getting public key for doctor", java.util.Map.of(
            "doctorId", doctorId
        ));
        
        Optional<Doctor> doctorOpt = doctorRepository.findById(doctorId);
        if (doctorOpt.isEmpty()) {
            logger.warn("Doctor not found", java.util.Map.of(
                "doctorId", doctorId
            ));
            throw new IllegalArgumentException("Doctor not found");
        }
        
        Doctor doctor = doctorOpt.get();
        User user = doctor.getUser();
        if (user.getPublicKey() == null) {
            logger.warn("Doctor does not have a public key", java.util.Map.of(
                "doctorId", doctorId
            ));
            throw new IllegalStateException("Doctor does not have a public key");
        }
        
        logger.info("Retrieved public key for doctor", java.util.Map.of(
            "doctorId", doctorId
        ));
        return user.getPublicKey();
    }

    /**
     * Récupère la liste des médecins associés à un patient.
     * 
     * @param patientId ID du patient
     * @return Liste des IDs des médecins
     */
    public List<UUID> getPatientDoctors(UUID patientId) {
        logger.debug("Getting doctors list for patient", java.util.Map.of(
            "patientId", patientId
        ));
        
        List<PatientDoctor> relations = patientDoctorRepository.findByPatientId(patientId);
        List<UUID> doctorIds = relations.stream()
            .map(rel -> rel.getDoctor().getId())
            .collect(Collectors.toList());
        
        logger.info("Patient has doctors", java.util.Map.of(
            "patientId", patientId,
            "count", doctorIds.size()
        ));
        return doctorIds;
    }

    public List<Map<String, String>> getPatientDoctorsWithKeys(UUID patientId) {
        logger.debug("Getting doctors with keys for patient", java.util.Map.of(
            "patientId", patientId
        ));
        
        List<PatientDoctor> relations = patientDoctorRepository.findByPatientId(patientId);
        List<Map<String, String>> result = relations.stream()
            .filter(rel -> rel.isApprovedByPatient())
            .map(rel -> {
                Doctor d = rel.getDoctor();
                String pem = d.getUser().getPublicKey();
                return Map.of(
                    "doctorId", d.getId().toString(),
                    "publicKeyPEM", pem == null ? "" : pem
                );
            })
            .collect(Collectors.toList());
        
        logger.info("Patient has approved doctors with keys", java.util.Map.of(
            "patientId", patientId,
            "count", result.size()
        ));
        return result;
    }

    /**
     * Supprime un médecin de la liste des médecins d'un patient.
     * 
     * Supprime:
     * 1. Les clés de fichiers médicaux chiffrées pour ce docteur
     * 2. L'entrée PatientSymmetricKey pour ce docteur (révoque accès aux données patient)
     * 3. La relation PatientDoctor elle-même
     * 
     * @param patientId ID du patient
     * @param doctorId ID du médecin
     */
    @Transactional
    public void removeDoctorFromPatient(UUID patientId, UUID doctorId) {
        logger.debug("Removing doctor from patient", java.util.Map.of(
            "doctorId", doctorId,
            "patientId", patientId
        ));
        
        PatientDoctorId id = new PatientDoctorId(patientId, doctorId);
        if (!patientDoctorRepository.existsById(id)) {
            logger.warn("Doctor is not associated with patient", java.util.Map.of(
                "doctorId", doctorId,
                "patientId", patientId
            ));
            throw new IllegalArgumentException("Doctor is not associated with this patient");
        }
        
        // 1) Revoke access to medical files: delete wrapped file keys for this doctor on this patient's files
        medicalFileKeyRepository.deleteDoctorKeysForPatient(doctorId, patientId);
        logger.debug("Revoked file access for doctor on patient", java.util.Map.of(
            "doctorId", doctorId,
            "patientId", patientId
        ));

        // 2) NOUVEAU: Revoke access to patient data: delete PatientSymmetricKey for this doctor
        patientSymmetricKeyRepository.deleteByPatientAndDoctor(patientId, doctorId);
        logger.debug("Revoked data access for doctor on patient", java.util.Map.of(
            "doctorId", doctorId,
            "patientId", patientId
        ));

        // 3) Remove the appointment link
        patientDoctorRepository.deleteById(id);
        logger.info("Doctor successfully removed from patient", java.util.Map.of(
            "doctorId", doctorId,
            "patientId", patientId
        ));
    }

    /**
     * Liste tous les médecins disponibles.
     * 
     * @return Liste des informations des médecins (nom, prénom, organisation en clair)
     */
    public List<DoctorInfoDTO> listAllDoctors() {
        logger.debug("Listing all doctors");
        
        List<Doctor> doctors = doctorRepository.findAll();
        List<DoctorInfoDTO> result = doctors.stream()
            .map(doctor -> {
                DoctorInfoDTO dto = new DoctorInfoDTO();
                dto.setDoctorId(doctor.getId().toString());
                // Les données personnelles sont maintenant dans Doctor, pas dans User
                dto.setFirstName(doctor.getFirstName());
                dto.setLastName(doctor.getLastName());
                dto.setMedicalOrganization(doctor.getMedicalOrganization());
                dto.setPublicKeyPEM(doctor.getUser().getPublicKey());
                return dto;
            })
            .collect(Collectors.toList());
        
        logger.info("Listed doctors", java.util.Map.of(
            "count", result.size()
        ));
        return result;
    }

    /**
     * Récupère la clé publique RSA d'un patient (PEM).
     * (Contrôles d'accès à faire côté contrôleur)
     */
    public String getPatientPublicKey(UUID patientId) {
        logger.debug("Getting public key for patient", java.util.Map.of(
            "patientId", patientId
        ));
        
        Optional<Patient> patientOpt = patientRepository.findById(patientId);
        if (patientOpt.isEmpty()) {
            logger.warn("Patient not found", java.util.Map.of(
                "patientId", patientId
            ));
            throw new IllegalArgumentException("Patient not found");
        }
        User user = patientOpt.get().getUser();
        String pem = user.getPublicKey();
        if (pem == null || pem.isBlank()) {
            logger.warn("Patient does not have a public key", java.util.Map.of(
                "patientId", patientId
            ));
            throw new IllegalStateException("Patient does not have a public key");
        }
        
        logger.info("Retrieved public key for patient", java.util.Map.of(
            "patientId", patientId
        ));
        return pem;
    }

    /**
     * Recherche des médecins par nom ou prénom (recherche insensible à la casse).
     * 
     * @param searchTerm Terme de recherche (nom ou prénom)
     * @return Liste des médecins correspondants
     */
    public List<DoctorInfoDTO> searchDoctorsByName(String searchTerm) {
        logger.debug("Searching doctors by name", java.util.Map.of(
            "searchTerm", searchTerm
        ));
        
        String lowerSearch = searchTerm.toLowerCase();
        List<Doctor> doctors = doctorRepository.findAll();
        List<DoctorInfoDTO> result = doctors.stream()
            .filter(doctor -> 
                doctor.getFirstName().toLowerCase().contains(lowerSearch) ||
                doctor.getLastName().toLowerCase().contains(lowerSearch)
            )
            .map(doctor -> {
                DoctorInfoDTO dto = new DoctorInfoDTO();
                dto.setDoctorId(doctor.getId().toString());
                dto.setFirstName(doctor.getFirstName());
                dto.setLastName(doctor.getLastName());
                dto.setMedicalOrganization(doctor.getMedicalOrganization());
                dto.setPublicKeyPEM(doctor.getUser().getPublicKey());
                return dto;
            })
            .collect(Collectors.toList());
        
        logger.info("Found doctors matching search term", java.util.Map.of(
            "count", result.size(),
            "searchTerm", searchTerm
        ));
        return result;
    }

    /**
     * Récupère la liste des patients d'un médecin avec leurs données chiffrées.
     * 
     * @param doctorId ID du médecin
     * @return Liste des patients avec données chiffrées + clé AES chiffrée
     */
    public List<DoctorPatientDTO> getDoctorPatients(UUID doctorId) {
        logger.debug("Getting patients for doctor", java.util.Map.of(
            "doctorId", doctorId
        ));
        List<PatientDoctor> relations = patientDoctorRepository.findByDoctorId(doctorId);
        logger.info("Doctor has patients", java.util.Map.of(
            "doctorId", doctorId,
            "count", relations.size()
        ));
        return relations.stream()
            .map(rel -> {
                Patient patient = rel.getPatient();
                return DoctorPatientDTO.builder()
                    .patientId(patient.getId().toString())
                    .firstNameEnc(Base64.getEncoder().encodeToString(patient.getFirstNameEnc()))
                    .lastNameEnc(Base64.getEncoder().encodeToString(patient.getLastNameEnc()))
                    .emailEnc(Base64.getEncoder().encodeToString(patient.getEmailEnc()))
                    .encryptedAESKey(Base64.getEncoder().encodeToString(rel.getEncryptedSymmetricKeyForDoctor()))
                    .build();
            })
            .collect(Collectors.toList());
    }

}

