package be.he2b.healthsec.medical_records.service;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
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
import be.he2b.healthsec.medical_records.dto.PatientInfoDTO;
import be.he2b.healthsec.medical_records.repository.DoctorRepository;
import be.he2b.healthsec.medical_records.repository.PatientDoctorRepository;
import be.he2b.healthsec.medical_records.repository.PatientRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PatientDoctorService {
    private final PatientDoctorRepository patientDoctorRepository;
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
                User user = doctor.getUser();
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());
                dto.setMedicalOrganization(doctor.getMedicalOrganization());
                dto.setPublicKeyPEM(user.getPublicKey());
                return dto;
            })
            .collect(Collectors.toList());
    }

    /**
     * Recherche des patients par nom et/ou prénom.
     * Utilisé par les médecins pour trouver des patients à qui demander l'accès.
     * 
     * @param firstName Prénom (optionnel, peut être null ou vide)
     * @param lastName Nom (optionnel, peut être null ou vide)
     * @return Liste des patients correspondants
     */
    public List<PatientInfoDTO> searchPatients(String firstName, String lastName) {
        List<Patient> patients = patientRepository.findAll();
        
        return patients.stream()
            .filter(patient -> {
                User user = patient.getUser();
                boolean firstNameMatch = firstName == null || firstName.trim().isEmpty() || 
                    user.getFirstName().toLowerCase().contains(firstName.toLowerCase());
                boolean lastNameMatch = lastName == null || lastName.trim().isEmpty() || 
                    user.getLastName().toLowerCase().contains(lastName.toLowerCase());
                return firstNameMatch && lastNameMatch;
            })
            .map(patient -> {
                PatientInfoDTO dto = new PatientInfoDTO();
                dto.setPatientId(patient.getId().toString());
                User user = patient.getUser();
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());
                dto.setPublicKeyPEM(user.getPublicKey());
                return dto;
            })
            .collect(Collectors.toList());
    }

    /**
     * Récupère la clé publique RSA d'un patient.
     * 
     * @param patientId ID du patient
     * @return Clé publique RSA en format PEM
     */
    public String getPatientPublicKey(UUID patientId) {
        Optional<Patient> patientOpt = patientRepository.findById(patientId);
        if (patientOpt.isEmpty()) {
            throw new IllegalArgumentException("Patient not found");
        }
        
        Patient patient = patientOpt.get();
        User user = patient.getUser();
        if (user.getPublicKey() == null) {
            throw new IllegalStateException("Patient does not have a public key");
        }
        
        return user.getPublicKey();
    }
}

