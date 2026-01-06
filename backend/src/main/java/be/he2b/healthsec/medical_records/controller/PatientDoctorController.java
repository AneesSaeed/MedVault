package be.he2b.healthsec.medical_records.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import be.he2b.healthsec.medical_records.dto.AddDoctorToPatientDTO;
import be.he2b.healthsec.medical_records.dto.DoctorInfoDTO;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.repository.PatientDoctorRepository;
import be.he2b.healthsec.medical_records.security.JwtRoles;
import be.he2b.healthsec.medical_records.service.PatientDoctorService;
import be.he2b.healthsec.medical_records.model.PatientDoctorId;
import be.he2b.healthsec.medical_records.service.UserService;
import be.he2b.healthsec.medical_records.logging.LoggingService;
import lombok.RequiredArgsConstructor;

/**
 * Patient/doctor relationship endpoints (appointments) and public-key retrieval.
 *
 * <p>Security model:
 * <ul>
 *   <li>Patients manage their appointed doctors.</li>
 *   <li>Doctors can access patient data/keys only when a PatientDoctor relation exists.</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/patient-doctor")
@RequiredArgsConstructor
@Validated
public class PatientDoctorController {
    
    private final PatientDoctorService patientDoctorService;
    private final UserService userService;
    private final PatientDoctorRepository patientDoctorRepository;
    private final LoggingService logger;

    /**
     * returns a doctor's RSA public key (PEM) so a patient can wrap a symmetric key.
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
     * returns a patient's RSA public key (PEM).
     */
    @GetMapping("/patient/{patientId}/public-key")
    public ResponseEntity<?> getPatientPublicKey(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String patientId
    ) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient-doctor/patient/" + patientId + "/public-key", keycloakId);
        
        try {
            UUID requestedPatientId = UUID.fromString(patientId);

            User caller = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

            if (JwtRoles.hasRealmRole(jwt, "PATIENT")) {
                if (!caller.getId().equals(requestedPatientId)) {
                    logger.logSecurityEvent("UNAUTHORIZED_PATIENT_KEY_ACCESS", keycloakId, "MEDIUM", Map.of(
                        "requestedPatientId", patientId,
                        "callerPatientId", caller.getId().toString()
                    ));
                    return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
                }
            } else if (JwtRoles.hasRealmRole(jwt, "DOCTOR")) {
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
                        "effectiveRole", String.valueOf(JwtRoles.effectiveRole(jwt))
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

    /**
     * returns the patient's appointed doctors with their associated encrypted key material.
     */
    @GetMapping("/my-doctors/keys")
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
     * lists doctors
     */
    @GetMapping("/doctors")
    public ResponseEntity<?> listAllDoctors(@RequestParam(required = false) String search) {
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
     * Appoints a doctor and stores the patient's AES key wrapped for that doctor.
     */
    @PostMapping("/add")
    public ResponseEntity<?> addDoctorToPatient(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddDoctorToPatientDTO dto
    ) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("POST", "/api/patient-doctor/add", keycloakId);
        
        try {
            if (!JwtRoles.hasRealmRole(jwt, "PATIENT")) {
                logger.logSecurityEvent("UNAUTHORIZED_DOCTOR_ADD", keycloakId, "MEDIUM", Map.of(
                        "reason", "JWT does not have PATIENT role",
                        "effectiveRole", String.valueOf(JwtRoles.effectiveRole(jwt))
                ));
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only patients can add doctors"));
            }

            User user = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

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
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    /**
     * Lists the patient's appointed doctors
     */
    @GetMapping("/my-doctors")
    public ResponseEntity<?> getMyDoctors(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient-doctor/my-doctors", keycloakId);

        try {
            if (!JwtRoles.hasRealmRole(jwt, "PATIENT")) {
                logger.logSecurityEvent("NON_PATIENT_MY_DOCTORS_ACCESS", keycloakId, "MEDIUM", Map.of(
                        "reason", "JWT does not have PATIENT role",
                        "effectiveRole", String.valueOf(JwtRoles.effectiveRole(jwt))
                ));
                return ResponseEntity.status(403).body(Map.of("error", "Only patients can view their doctors"));
            }

            User user = userService.findByKeycloakId(keycloakId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            List<UUID> doctorIds = patientDoctorService.getPatientDoctors(user.getId());

            logger.info("Patient retrieved their doctors list", Map.of(
                    "patientId", user.getId().toString(),
                    "doctorsCount", doctorIds.size()
            ));

            return ResponseEntity.ok(Map.of("doctorIds", doctorIds));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to get patient doctors: " + e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * lists patients appointed to the doctor.
     * Returned patient data remains encrypted (client-side decryption).
     */
    @GetMapping("/my-patients")
    public ResponseEntity<?> getMyPatients(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient-doctor/my-patients", keycloakId);

        try {
            if (!JwtRoles.hasRealmRole(jwt, "DOCTOR")) {
                logger.logSecurityEvent("NON_DOCTOR_MY_PATIENTS_ACCESS", keycloakId, "MEDIUM", Map.of(
                        "reason", "JWT does not have DOCTOR role",
                        "effectiveRole", String.valueOf(JwtRoles.effectiveRole(jwt))
                ));
                return ResponseEntity.status(403).body(Map.of("error", "Only doctors can view their patients"));
            }

            User user = userService.findByKeycloakId(keycloakId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            var patients = patientDoctorService.getDoctorPatients(user.getId());

            logger.info("Doctor retrieved their patients list", Map.of(
                    "doctorId", user.getId().toString(),
                    "patientsCount", patients.size()
            ));
            return ResponseEntity.ok(Map.of("patients", patients));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to get doctor patients: " + e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * returns encrypted patient data for a specific patient.
     * Access is restricted to doctors appointed to that patient
     */
    @GetMapping("/patient/{patientId}/data")
    public ResponseEntity<?> getPatientData(
                @AuthenticationPrincipal Jwt jwt,
                @PathVariable String patientId
    ) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient-doctor/patient/" + patientId + "/data", keycloakId);

        try {
            if (!JwtRoles.hasRealmRole(jwt, "DOCTOR")) {
                logger.logSecurityEvent("NON_DOCTOR_PATIENT_DATA_ACCESS", keycloakId, "HIGH", Map.of(
                        "reason", "JWT does not have DOCTOR role",
                        "effectiveRole", String.valueOf(JwtRoles.effectiveRole(jwt)),
                        "patientId", patientId
                ));
                return ResponseEntity.status(403).body(Map.of("error", "Only doctors can view patient data"));
            }

            User user = userService.findByKeycloakId(keycloakId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            UUID doctorId = user.getId();
            UUID patientUuid = UUID.fromString(patientId);

            var patientData = userService.getPatientData(patientUuid, doctorId);

            logger.logAction("DOCTOR_ACCESSED_PATIENT_DATA", keycloakId, Map.of(
                    "doctorId", doctorId.toString(),
                    "patientId", patientId
            ));

            return ResponseEntity.ok(patientData);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to get patient data: " + e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    /**
     * removes an appointed doctor from the patient.
     */
    @DeleteMapping("/remove/{doctorId}")
    public ResponseEntity<?> removeDoctorFromPatient(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable String doctorId) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("DELETE", "/api/patient-doctor/remove/" + doctorId, keycloakId);

        try {
            if (!JwtRoles.hasRealmRole(jwt, "PATIENT")) {
                logger.logSecurityEvent("NON_PATIENT_REMOVE_DOCTOR", keycloakId, "MEDIUM", Map.of(
                        "reason", "JWT does not have PATIENT role",
                        "effectiveRole", String.valueOf(JwtRoles.effectiveRole(jwt)),
                        "doctorId", doctorId
                ));
                return ResponseEntity.status(403).body(Map.of("error", "Only patients can remove doctors"));
            }

            User user = userService.findByKeycloakId(keycloakId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

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
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    // ---- helpers ----    
    
    private UUID currentPatientIdOrThrow(Jwt jwt) {
        if (!JwtRoles.hasRealmRole(jwt, "PATIENT")) {
            throw new IllegalArgumentException("Only patients can perform this action");
        }
        String keycloakId = jwt.getSubject();
        User user = userService.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getId();
    }
}

