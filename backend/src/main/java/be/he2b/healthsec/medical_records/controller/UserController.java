package be.he2b.healthsec.medical_records.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import be.he2b.healthsec.medical_records.dto.CreateDoctorDTO;
import be.he2b.healthsec.medical_records.dto.CreatePatientDTO;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.service.UserService;

@RestController
@RequestMapping("/api")
public class UserController {

    @Autowired
    private UserService userService;

    // --------------------------------------------------------------
    //  Check if the current authenticated Keycloak user exists
    // --------------------------------------------------------------
    @GetMapping("/user/exists")
    public ResponseEntity<Boolean> userExists(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        boolean exists = userService.existsByKeycloakId(keycloakId);
        return ResponseEntity.ok(exists);
    }

    // --------------------------------------------------------------
    //  Get user’s info (after onboarding)
    // --------------------------------------------------------------
    @GetMapping("/user/me")
    public ResponseEntity<?> userMe(@AuthenticationPrincipal Jwt jwt) {

        String keycloakId = jwt.getSubject();

        Optional<User> userOpt = userService.findByKeycloakId(keycloakId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(null);
        }

        User user = userOpt.get();

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
        try {
            String keycloakId = jwt.getSubject();
            
            String msg = userService.createPatient(
                keycloakId,
                dto.getFirstNameEncBase64(),
                dto.getLastNameEncBase64(),
                dto.getEmailEncBase64(),
                dto.getDateOfBirthEncBase64(),
                dto.getPublicKeyPEM()
            );

            return ResponseEntity.ok(
                Map.of("message", msg)
            );

        } catch (IllegalArgumentException e) {
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
        try {
            String keycloakId = jwt.getSubject();
            
            String msg = userService.createDoctor(
                keycloakId,
                dto.getFirstName(),
                dto.getLastName(),
                dto.getEmail(),
                dto.getMedicalOrganization(),
                dto.getPublicKeyPEM()
            );

            return ResponseEntity.ok(
                Map.of("message", msg)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }
}
