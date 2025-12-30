package be.he2b.healthsec.medical_records.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import be.he2b.healthsec.medical_records.dto.AddDoctorToPatientDTO;
import be.he2b.healthsec.medical_records.dto.DoctorInfoDTO;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.model.UserType;
import be.he2b.healthsec.medical_records.service.PatientDoctorService;
import be.he2b.healthsec.medical_records.model.PatientDoctorId;
import be.he2b.healthsec.medical_records.service.UserService;
import be.he2b.healthsec.medical_records.logging.LoggingService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/patient-doctor")
@RequiredArgsConstructor
@Validated
public class PatientDoctorController {
    private final PatientDoctorService patientDoctorService;
    private final UserService userService;
    private final be.he2b.healthsec.medical_records.repository.PatientDoctorRepository patientDoctorRepository;
    private final LoggingService logger;

    /**
     * Récupère la clé publique RSA d'un médecin.
     * Permet à un patient de récupérer la clé publique d'un médecin
     * pour chiffrer sa clé AES avant de l'ajouter.
     */
    @GetMapping("/doctor/{doctorId}/public-key")
    public ResponseEntity<?> getDoctorPublicKey(@PathVariable String doctorId) {
        logger.logApiRequest("GET", "/api/patient-doctor/doctor/" + doctorId + "/public-key", "anonymous");
        try {
            UUID doctorUuid = UUID.fromString(doctorId);
            String publicKey = patientDoctorService.getDoctorPublicKey(doctorUuid);
            logger.info("Doctor public key accessed", Map.of("doctorId", doctorId));
            return ResponseEntity.ok(Map.of("publicKeyPEM", publicKey));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to get doctor public key", Map.of("doctorId", doctorId, "error", e.getMessage()));
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Récupère la clé publique RSA d'un patient.
     * Autorisé pour:
     * - Le patient lui-même
     * - Un médecin qui a une relation PatientDoctor avec ce patient
     */
    @GetMapping("/patient/{patientId}/public-key")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPatientPublicKey(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable String patientId) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient-doctor/patient/" + patientId + "/public-key", keycloakId);
        
        try {
            UUID requestedPatientId = UUID.fromString(patientId);
            User caller = userService.findByKeycloakId(keycloakId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            if (caller.getRole() == UserType.PATIENT) {
                // Patient ne peut demander que sa propre clé publique
                if (!caller.getId().equals(requestedPatientId)) {
                    logger.logSecurityEvent("UNAUTHORIZED_PATIENT_KEY_ACCESS", keycloakId, "MEDIUM", Map.of(
                        "requestedPatientId", patientId,
                        "callerPatientId", caller.getId().toString()
                    ));
                    return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
                }
            } else if (caller.getRole() == UserType.DOCTOR) {
                // Docteur doit avoir une relation avec ce patient
                PatientDoctorId relId = new PatientDoctorId(requestedPatientId, caller.getId());
                if (!patientDoctorRepository.existsById(relId)) {
                    logger.logSecurityEvent("UNAUTHORIZED_DOCTOR_PATIENT_KEY_ACCESS", keycloakId, "MEDIUM", Map.of(
                        "patientId", patientId,
                        "doctorId", caller.getId().toString()
                    ));
                    return ResponseEntity.status(403).body(Map.of("error", "Doctor not linked to patient"));
                }
            } else {
                logger.logSecurityEvent("INVALID_ROLE_PATIENT_KEY_ACCESS", keycloakId, "MEDIUM", Map.of(
                    "role", caller.getRole().toString()
                ));
                return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
            }

            String publicKeyPEM = patientDoctorService.getPatientPublicKey(requestedPatientId);
            logger.info("Patient public key accessed", Map.of(
                "patientId", patientId,
                "requesterId", keycloakId
            ));
            return ResponseEntity.ok(Map.of("publicKeyPEM", publicKeyPEM));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to get patient public key: " + e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my-doctors/keys")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> myDoctorsWithKeys(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient-doctor/my-doctors/keys", keycloakId);
        
        UUID patientId = currentPatientIdOrThrow(jwt); 
        var doctors = patientDoctorService.getPatientDoctorsWithKeys(patientId);
        logger.info("Patient retrieved doctors with keys", Map.of(
            "patientId", patientId.toString(),
            "doctorsCount", doctors.size()
        ));
        return ResponseEntity.ok(Map.of("doctors", doctors));
    }


    /**
     * Liste tous les médecins disponibles (pour qu'un patient puisse les rechercher).
     * Optionnellement, filtre par nom/prénom.
     * Retourne les informations en clair (nom, prénom, organisation).
     */
    @GetMapping("/doctors")
    public ResponseEntity<?> listAllDoctors(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search) {
        logger.logApiRequest("GET", "/api/patient-doctor/doctors", "anonymous");
        
        try {
            List<DoctorInfoDTO> doctors;
            if (search != null && !search.trim().isEmpty()) {
                doctors = patientDoctorService.searchDoctorsByName(search.trim());
                logger.info("Doctors searched", Map.of(
                    "searchTerm", search.trim(),
                    "resultsCount", doctors.size()
                ));
            } else {
                doctors = patientDoctorService.listAllDoctors();
                logger.info("All doctors listed", Map.of(
                    "doctorsCount", doctors.size()
                ));
            }
            return ResponseEntity.ok(Map.of("doctors", doctors));
        } catch (Exception e) {
            logger.error("Failed to list doctors: " + e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Ajoute un médecin à la liste des médecins d'un patient.
     * 
     * Selon l'énoncé : "A patient can add or remove a doctor to his list of appointed doctors."
     */
    @PostMapping("/add")
        @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addDoctorToPatient(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddDoctorToPatientDTO dto) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("POST", "/api/patient-doctor/add", keycloakId);
        
        try {
            // Récupère l'ID du patient depuis le JWT
            User user = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (user.getRole() != be.he2b.healthsec.medical_records.model.UserType.PATIENT) {
                logger.logSecurityEvent("UNAUTHORIZED_DOCTOR_ADD", keycloakId, "MEDIUM", Map.of(
                    "reason", "User is not a patient",
                    "userRole", user.getRole().name()
                ));
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only patients can add doctors"));
            }

            UUID patientId = user.getId();
            UUID doctorId = UUID.fromString(dto.getDoctorId());

            String msg = patientDoctorService.addDoctorToPatient(
                patientId,
                doctorId,
                dto.getEncryptedPatientAESKeyBase64()
            );

            logger.logAction("PATIENT_ADDED_DOCTOR", keycloakId, Map.of(
                "patientId", patientId.toString(),
                "doctorId", dto.getDoctorId(),
                "hasEncryptedKey", dto.getEncryptedPatientAESKeyBase64() != null
            ));

            return ResponseEntity.ok(Map.of("message", msg));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to add doctor to patient: " + e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Récupère la liste des médecins associés au patient actuel.
     */
    @GetMapping("/my-doctors")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyDoctors(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient-doctor/my-doctors", keycloakId);
        
        try {
            User user = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (user.getRole() != be.he2b.healthsec.medical_records.model.UserType.PATIENT) {
                logger.logSecurityEvent("NON_PATIENT_MY_DOCTORS_ACCESS", keycloakId, "MEDIUM", Map.of(
                    "userRole", user.getRole().toString()
                ));
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only patients can view their doctors"));
            }

            List<UUID> doctorIds = patientDoctorService.getPatientDoctors(user.getId());
            logger.info("Patient retrieved their doctors list", Map.of(
                "patientId", user.getId().toString(),
                "doctorsCount", doctorIds.size()
            ));
            return ResponseEntity.ok(Map.of("doctorIds", doctorIds));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to get patient doctors: " + e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Récupère la liste des patients du médecin actuellement authentifié.
     * Retourne les IDs des patients + données chiffrées (jamais déchiffré côté serveur).
     */
    @GetMapping("/my-patients")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyPatients(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient-doctor/my-patients", keycloakId);
        
        try {
            User user = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (user.getRole() != be.he2b.healthsec.medical_records.model.UserType.DOCTOR) {
                logger.logSecurityEvent("NON_DOCTOR_MY_PATIENTS_ACCESS", keycloakId, "MEDIUM", Map.of(
                    "userRole", user.getRole().toString()
                ));
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only doctors can view their patients"));
            }

            var patients = patientDoctorService.getDoctorPatients(user.getId());
            logger.info("Doctor retrieved their patients list", Map.of(
                "doctorId", user.getId().toString(),
                "patientsCount", patients.size()
            ));
            return ResponseEntity.ok(Map.of("patients", patients));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to get doctor patients: " + e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Récupère les données chiffrées d'un patient spécifique.
     * Les données restent chiffrées (déchiffrement côté client).
     * Le médecin doit avoir accès au patient via une relation PatientDoctor.
     */
    @GetMapping("/patient/{patientId}/data")
    public ResponseEntity<?> getPatientData(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String patientId) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient-doctor/patient/" + patientId + "/data", keycloakId);
        
        try {
            User user = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (user.getRole() != be.he2b.healthsec.medical_records.model.UserType.DOCTOR) {
                logger.logSecurityEvent("NON_DOCTOR_PATIENT_DATA_ACCESS", keycloakId, "HIGH", Map.of(
                    "userRole", user.getRole().toString(),
                    "patientId", patientId
                ));
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only doctors can view patient data"));
            }

            UUID doctorId = user.getId();
            UUID patientUuid = UUID.fromString(patientId);

            // Use UserService.getPatientData() which validates access via PatientSymmetricKey
            var patientData = userService.getPatientData(patientUuid, doctorId);
            logger.logAction("DOCTOR_ACCESSED_PATIENT_DATA", keycloakId, Map.of(
                "doctorId", doctorId.toString(),
                "patientId", patientId
            ));
            return ResponseEntity.ok(patientData);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to get patient data: " + e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Supprime un médecin de la liste des médecins d'un patient.
     */
    @DeleteMapping("/remove/{doctorId}")
    public ResponseEntity<?> removeDoctorFromPatient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String doctorId) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("DELETE", "/api/patient-doctor/remove/" + doctorId, keycloakId);
        
        try {
            User user = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (user.getRole() != be.he2b.healthsec.medical_records.model.UserType.PATIENT) {
                logger.logSecurityEvent("NON_PATIENT_REMOVE_DOCTOR", keycloakId, "MEDIUM", Map.of(
                    "userRole", user.getRole().toString(),
                    "doctorId", doctorId
                ));
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only patients can remove doctors"));
            }

            UUID patientId = user.getId();
            UUID doctorUuid = UUID.fromString(doctorId);

            patientDoctorService.removeDoctorFromPatient(patientId, doctorUuid);
            logger.logAction("PATIENT_REMOVED_DOCTOR", keycloakId, Map.of(
                "patientId", patientId.toString(),
                "doctorId", doctorId
            ));
            return ResponseEntity.ok(Map.of("message", "Doctor removed successfully"));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to remove doctor: " + e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // Helpers
    private UUID currentPatientIdOrThrow(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        User user = userService.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRole() != UserType.PATIENT) {
            throw new IllegalArgumentException("Only patients can manage medical files");
        }
        return user.getId();
    }
}

