package be.he2b.healthsec.medical_records.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import be.he2b.healthsec.medical_records.dto.CreateDoctorDTO;
import be.he2b.healthsec.medical_records.dto.CreatePatientDTO;
import be.he2b.healthsec.medical_records.dto.PatientDataDTO;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.service.UserService;
import be.he2b.healthsec.medical_records.logging.LoggingService;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private LoggingService logger;

    // --------------------------------------------------------------
    //  Check if the current authenticated Keycloak user exists
    // --------------------------------------------------------------
    @GetMapping("/user/exists")
    public ResponseEntity<Boolean> userExists(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/user/exists", keycloakId);
        boolean exists = userService.existsByKeycloakId(keycloakId);
        return ResponseEntity.ok(exists);
    }

    // --------------------------------------------------------------
    //  Get user’s info (after onboarding)
    // --------------------------------------------------------------
    @GetMapping("/user/me")
    public ResponseEntity<?> userMe(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/user/me", keycloakId);

        Optional<User> userOpt = userService.findByKeycloakId(keycloakId);
        if (userOpt.isEmpty()) {
            logger.debug("User not found in database", Map.of(
                "keycloakId", keycloakId
            ));
            return ResponseEntity.ok(null);
        }

        User user = userOpt.get();
        logger.info("User info retrieved", Map.of(
            "userId", user.getId().toString(),
            "role", user.getRole().name()
        ));

        return ResponseEntity.ok(
            Map.of(
                "userId", user.getId(),
                "role", user.getRole().name()
            )
        );
    }

    // --------------------------------------------------------------
    //  Create Patient (first-time onboarding)
    // --------------------------------------------------------------
    @PostMapping("/patient")
    public ResponseEntity<?> createPatient(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreatePatientDTO dto) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("POST", "/api/patient", keycloakId);
        
        try {
            String msg = userService.createPatient(
                keycloakId,
                dto.getFirstNameEncBase64(),
                dto.getLastNameEncBase64(),
                dto.getEmailEncBase64(),
                dto.getDateOfBirthEncBase64(),
                dto.getPublicKeyPEM(),
                dto.getSymmetricKeyEncBase64()
            );

            logger.logAction("PATIENT_CREATED", keycloakId, Map.of(
                "hasPublicKey", dto.getPublicKeyPEM() != null,
                "hasEncryptedData", true
            ));

            return ResponseEntity.ok(
                Map.of("message", msg)
            );

        } catch (IllegalArgumentException e) {
            logger.error("Failed to create patient: " + e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }


    // --------------------------------------------------------------
    //  Create Doctor (first-time onboarding)
    // --------------------------------------------------------------
    @PostMapping("/doctor")
    public ResponseEntity<?> createDoctor(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateDoctorDTO dto) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("POST", "/api/doctor", keycloakId);
        
        try {
            String msg = userService.createDoctor(
                keycloakId,
                dto.getFirstName(),
                dto.getLastName(),
                dto.getEmail(),
                dto.getMedicalOrganization(),
                dto.getPublicKeyPEM()
            );

            logger.logAction("DOCTOR_CREATED", keycloakId, Map.of(
                "organization", dto.getMedicalOrganization(),
                "email", dto.getEmail(),
                "hasPublicKey", dto.getPublicKeyPEM() != null
            ));

            return ResponseEntity.ok(
                Map.of("message", msg)
            );

        } catch (IllegalArgumentException e) {
            logger.error("Failed to create doctor: " + e.getMessage(), e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // --------------------------------------------------------------
    //  Get patient data with encrypted symmetric key
    // --------------------------------------------------------------
    @GetMapping("/patient/{patientId}/data")
    public ResponseEntity<?> getPatientData(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String patientId) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient/" + patientId + "/data", keycloakId);
        
        try {
            // Récupérer l'ID UUID du patient depuis le keycloakId pour vérifier
            Optional<User> userOpt = userService.findByKeycloakId(keycloakId);
            if (userOpt.isEmpty()) {
                logger.logSecurityEvent("UNAUTHORIZED_ACCESS", keycloakId, "HIGH", Map.of(
                    "reason", "User not authenticated",
                    "endpoint", "/api/patient/" + patientId + "/data"
                ));
                return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
            }
            java.util.UUID userId = userOpt.get().getId();
            java.util.UUID patientUUID = java.util.UUID.fromString(patientId);

            PatientDataDTO patientData = userService.getPatientData(patientUUID, userId);
            
            logger.logAction("PATIENT_DATA_ACCESSED", keycloakId, Map.of(
                "patientId", patientId,
                "requesterId", userId.toString()
            ));
            
            return ResponseEntity.ok(patientData);

        } catch (IllegalArgumentException e) {
            logger.logSecurityEvent("ACCESS_DENIED", keycloakId, "MEDIUM", Map.of(
                "reason", e.getMessage(),
                "patientId", patientId
            ));
            return ResponseEntity.status(403)
                .body(Map.of("error", e.getMessage()));
        }
    }
}
