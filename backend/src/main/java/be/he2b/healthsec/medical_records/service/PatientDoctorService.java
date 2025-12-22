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
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.dto.DoctorInfoDTO;
import be.he2b.healthsec.medical_records.repository.DoctorRepository;
import be.he2b.healthsec.medical_records.repository.MedicalFileKeyRepository;
import be.he2b.healthsec.medical_records.repository.PatientDoctorRepository;
import be.he2b.healthsec.medical_records.repository.PatientRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PatientDoctorService {
    private final PatientDoctorRepository patientDoctorRepository;
    private final MedicalFileKeyRepository medicalFileKeyRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    /**
     * Ajoute un médecin à la liste des médecins d'un patient.
     * 
     * Selon l'énoncé : "A patient can add or remove a doctor to his list of appointed doctors."
     * 
     * @param patientId ID du patient
     * @param doctorId ID du médecin
     * @param encryptedPatientAESKeyBase64 Clé AES du patient chiffrée avec la clé publique RSA du médecin
     * @return Message de confirmation
     */
    @Transactional
    public String addDoctorToPatient(UUID patientId, UUID doctorId, 
                                     String encryptedPatientAESKeyBase64) {
        // Vérifie que le patient existe
        Optional<Patient> patientOpt = patientRepository.findById(patientId);
        if (patientOpt.isEmpty()) {
            throw new IllegalArgumentException("Patient not found");
        }
        Patient patient = patientOpt.get();

        // Vérifie que le médecin existe
        Optional<Doctor> doctorOpt = doctorRepository.findById(doctorId);
        if (doctorOpt.isEmpty()) {
            throw new IllegalArgumentException("Doctor not found");
        }
        Doctor doctor = doctorOpt.get();

        // Vérifie que la relation n'existe pas déjà
        PatientDoctorId id = new PatientDoctorId(patientId, doctorId);
        if (patientDoctorRepository.existsById(id)) {
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

        return "Doctor added to patient's list successfully";
    }

    /**
     * Récupère la clé publique RSA d'un médecin.
     * 
     * @param doctorId ID du médecin
     * @return Clé publique RSA en format PEM
     */
    public String getDoctorPublicKey(UUID doctorId) {
        Optional<Doctor> doctorOpt = doctorRepository.findById(doctorId);
        if (doctorOpt.isEmpty()) {
            throw new IllegalArgumentException("Doctor not found");
        }
        
        Doctor doctor = doctorOpt.get();
        User user = doctor.getUser();
        if (user.getPublicKey() == null) {
            throw new IllegalStateException("Doctor does not have a public key");
        }
        
        return user.getPublicKey();
    }

    /**
     * Récupère la liste des médecins associés à un patient.
     * 
     * @param patientId ID du patient
     * @return Liste des IDs des médecins
     */
    public List<UUID> getPatientDoctors(UUID patientId) {
        List<PatientDoctor> relations = patientDoctorRepository.findByPatientId(patientId);
        return relations.stream()
            .map(rel -> rel.getDoctor().getId())
            .collect(Collectors.toList());
    }

    public List<Map<String, String>> getPatientDoctorsWithKeys(UUID patientId) {
        List<PatientDoctor> relations = patientDoctorRepository.findByPatientId(patientId);
        return relations.stream()
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
    }

    /**
     * Supprime un médecin de la liste des médecins d'un patient.
     * 
     * @param patientId ID du patient
     * @param doctorId ID du médecin
     */
    @Transactional
    public void removeDoctorFromPatient(UUID patientId, UUID doctorId) {
        PatientDoctorId id = new PatientDoctorId(patientId, doctorId);
        if (!patientDoctorRepository.existsById(id)) {
            throw new IllegalArgumentException("Doctor is not associated with this patient");
        }
        
        // 1) revoke access: delete wrapped file keys for this doctor on this patient’s files
        medicalFileKeyRepository.deleteDoctorKeysForPatient(doctorId, patientId);

        // 2) remove the appointment link
        patientDoctorRepository.deleteById(id);
    }

    /**
     * Liste tous les médecins disponibles.
     * 
     * @return Liste des informations des médecins (nom, prénom, organisation en clair)
     */
    public List<DoctorInfoDTO> listAllDoctors() {
        List<Doctor> doctors = doctorRepository.findAll();
        return doctors.stream()
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
    }

    /**
     * Recherche des médecins par nom ou prénom (recherche insensible à la casse).
     * 
     * @param searchTerm Terme de recherche (nom ou prénom)
     * @return Liste des médecins correspondants
     */
    public List<DoctorInfoDTO> searchDoctorsByName(String searchTerm) {
        String lowerSearch = searchTerm.toLowerCase();
        List<Doctor> doctors = doctorRepository.findAll();
        return doctors.stream()
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
    }

    /**
     * Récupère la liste des patients d'un médecin avec leurs données chiffrées.
     * 
     * @param doctorId ID du médecin
     * @return Liste des patients avec données chiffrées + clé AES chiffrée
     */
    public List<?> getDoctorPatients(UUID doctorId) {
        List<PatientDoctor> relations = patientDoctorRepository.findByDoctorId(doctorId);
        return relations.stream()
            .map(rel -> {
                Patient patient = rel.getPatient();
                return Map.of(
                    "patientId", patient.getId().toString(),
                    "firstNameEnc", Base64.getEncoder().encodeToString(patient.getFirstNameEnc()),
                    "lastNameEnc", Base64.getEncoder().encodeToString(patient.getLastNameEnc()),
                    "emailEnc", Base64.getEncoder().encodeToString(patient.getEmailEnc()),
                    "encryptedAESKey", Base64.getEncoder().encodeToString(rel.getEncryptedSymmetricKeyForDoctor())
                );
            })
            .collect(Collectors.toList());
    }

    /**
     * Récupère les données chiffrées d'un patient spécifique.
     * Vérifie que le médecin a accès au patient via une relation PatientDoctor.
     * 
     * @param doctorId ID du médecin
     * @param patientId ID du patient
     * @return Données chiffrées du patient + clé AES chiffrée
     */
    public Map<String, String> getPatientEncryptedData(UUID doctorId, UUID patientId) {
        // Vérifie que la relation PatientDoctor existe
        PatientDoctorId id = new PatientDoctorId(patientId, doctorId);
        Optional<PatientDoctor> relationOpt = patientDoctorRepository.findById(id);
        if (relationOpt.isEmpty()) {
            throw new IllegalArgumentException("Doctor does not have access to this patient");
        }

        PatientDoctor relation = relationOpt.get();
        Patient patient = relation.getPatient();

        return Map.of(
            "patientId", patient.getId().toString(),
            "firstNameEnc", Base64.getEncoder().encodeToString(patient.getFirstNameEnc()),
            "lastNameEnc", Base64.getEncoder().encodeToString(patient.getLastNameEnc()),
            "emailEnc", Base64.getEncoder().encodeToString(patient.getEmailEnc()),
            "dateOfBirthEnc", Base64.getEncoder().encodeToString(patient.getDateOfBirthEnc()),
            "encryptedAESKey", Base64.getEncoder().encodeToString(relation.getEncryptedSymmetricKeyForDoctor())
        );
    }

}

