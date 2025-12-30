package be.he2b.healthsec.medical_records.controller;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import be.he2b.healthsec.medical_records.dto.CreateDoctorDTO;
import be.he2b.healthsec.medical_records.dto.CreatePatientDTO;
import be.he2b.healthsec.medical_records.dto.MeResponseDTO;
import be.he2b.healthsec.medical_records.dto.PatientDataDTO;
import be.he2b.healthsec.medical_records.logging.LoggingService;
import be.he2b.healthsec.medical_records.model.Doctor;
import be.he2b.healthsec.medical_records.model.Patient;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.security.JwtRoles;
import be.he2b.healthsec.medical_records.service.UserService;
import be.he2b.healthsec.medical_records.service.keycloak.KeycloakAdminService;

@RestController
@RequestMapping("/api")
@Validated
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private KeycloakAdminService keycloakAdminService;

    @Autowired
    private LoggingService logger;

    // --------------------------------------------------------------
    //  Check if the current authenticated Keycloak user exists
    // --------------------------------------------------------------
    @GetMapping("/user/exists")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> userMe(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/user/me", keycloakId);

        String role = JwtRoles.effectiveRole(jwt);

        MeResponseDTO me = userService.getMe(keycloakId, role);
        if (me == null) {
            logger.debug("User not found in database", Map.of("keycloakId", keycloakId));
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(me);
    }


    // --------------------------------------------------------------
    //  Create Patient (first-time onboarding)
    // --------------------------------------------------------------
    @PostMapping("/patient")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createPatient(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePatientDTO dto) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("POST", "/api/patient", keycloakId);

        String selectedRole = jwt.getClaimAsString("selected_role");
        if (!"PATIENT".equals(selectedRole)) {
            logger.logSecurityEvent("INVALID_SELECTED_ROLE_CREATE_PATIENT", keycloakId, "MEDIUM", Map.of(
                    "selected_role", String.valueOf(selectedRole)
            ));
            return ResponseEntity.status(403).body(Map.of("error", "selected_role must be PATIENT"));
        }

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

            // Assign Keycloak realm role
            keycloakAdminService.assignRealmRole(keycloakId, "PATIENT");

            logger.logAction("PATIENT_CREATED", keycloakId, Map.of(
                    "hasPublicKey", dto.getPublicKeyPEM() != null,
                    "hasEncryptedData", true
            ));

            return ResponseEntity.ok(Map.of("message", msg));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to create patient: " + e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --------------------------------------------------------------
    //  Create Doctor (first-time onboarding)
    // --------------------------------------------------------------
    @PostMapping("/doctor")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createDoctor(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateDoctorDTO dto) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("POST", "/api/doctor", keycloakId);

        String selectedRole = jwt.getClaimAsString("selected_role");
        if (!"DOCTOR".equals(selectedRole)) {
            logger.logSecurityEvent("INVALID_SELECTED_ROLE_CREATE_DOCTOR", keycloakId, "MEDIUM", Map.of(
                    "selected_role", String.valueOf(selectedRole)
            ));
            return ResponseEntity.status(403).body(Map.of("error", "selected_role must be DOCTOR"));
        }

        try {
            String msg = userService.createDoctor(
                    keycloakId,
                    dto.getFirstName(),
                    dto.getLastName(),
                    dto.getEmail(),
                    dto.getMedicalOrganization(),
                    dto.getPublicKeyPEM()
            );

            // Assign Keycloak realm role
            keycloakAdminService.assignRealmRole(keycloakId, "DOCTOR");

            logger.logAction("DOCTOR_CREATED", keycloakId, Map.of(
                    "organization", dto.getMedicalOrganization(),
                    "email", dto.getEmail(),
                    "hasPublicKey", dto.getPublicKeyPEM() != null
            ));

            return ResponseEntity.ok(Map.of("message", msg));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to create doctor: " + e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // --------------------------------------------------------------
    //  Get patient data with encrypted symmetric key
    // --------------------------------------------------------------
    @GetMapping("/patient/{patientId}/data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPatientData(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String patientId) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient/" + patientId + "/data", keycloakId);

        try {
            Optional<User> userOpt = userService.findByKeycloakId(keycloakId);
            if (userOpt.isEmpty()) {
                logger.logSecurityEvent("UNAUTHORIZED_ACCESS", keycloakId, "HIGH", Map.of(
                        "reason", "User not authenticated",
                        "endpoint", "/api/patient/" + patientId + "/data"
                ));
                return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
            }

            UUID requesterId = userOpt.get().getId();
            UUID patientUUID = UUID.fromString(patientId);

            PatientDataDTO patientData = userService.getPatientData(patientUUID, requesterId);

            logger.logAction("PATIENT_DATA_ACCESSED", keycloakId, Map.of(
                    "patientId", patientId,
                    "requesterId", requesterId.toString()
            ));

            return ResponseEntity.ok(patientData);

        } catch (IllegalArgumentException e) {
            logger.logSecurityEvent("ACCESS_DENIED", keycloakId, "MEDIUM", Map.of(
                    "reason", e.getMessage(),
                    "patientId", patientId
            ));
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }
}
