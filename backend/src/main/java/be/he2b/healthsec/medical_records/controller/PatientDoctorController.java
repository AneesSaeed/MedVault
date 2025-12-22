package be.he2b.healthsec.medical_records.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import be.he2b.healthsec.medical_records.dto.AddDoctorToPatientDTO;
import be.he2b.healthsec.medical_records.dto.DoctorInfoDTO;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.model.UserType;
import be.he2b.healthsec.medical_records.service.PatientDoctorService;
import be.he2b.healthsec.medical_records.service.UserService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/patient-doctor")
@RequiredArgsConstructor
public class PatientDoctorController {
    private final PatientDoctorService patientDoctorService;
    private final UserService userService;

    /**
     * Récupère la clé publique RSA d'un médecin.
     * Permet à un patient de récupérer la clé publique d'un médecin
     * pour chiffrer sa clé AES avant de l'ajouter.
     */
    @GetMapping("/doctor/{doctorId}/public-key")
    public ResponseEntity<?> getDoctorPublicKey(@PathVariable String doctorId) {
        try {
            UUID doctorUuid = UUID.fromString(doctorId);
            String publicKey = patientDoctorService.getDoctorPublicKey(doctorUuid);
            return ResponseEntity.ok(Map.of("publicKeyPEM", publicKey));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my-doctors/keys")
    public ResponseEntity<?> myDoctorsWithKeys(@AuthenticationPrincipal Jwt jwt) {
        UUID patientId = currentPatientIdOrThrow(jwt); 
        return ResponseEntity.ok(Map.of("doctors", patientDoctorService.getPatientDoctorsWithKeys(patientId)));
    }


    /**
     * Liste tous les médecins disponibles (pour qu'un patient puisse les rechercher).
     * Optionnellement, filtre par nom/prénom.
     * Retourne les informations en clair (nom, prénom, organisation).
     */
    @GetMapping("/doctors")
    public ResponseEntity<?> listAllDoctors(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search) {
        try {
            List<DoctorInfoDTO> doctors;
            if (search != null && !search.trim().isEmpty()) {
                doctors = patientDoctorService.searchDoctorsByName(search.trim());
            } else {
                doctors = patientDoctorService.listAllDoctors();
            }
            return ResponseEntity.ok(Map.of("doctors", doctors));
        } catch (Exception e) {
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
    public ResponseEntity<?> addDoctorToPatient(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AddDoctorToPatientDTO dto) {
        try {
            // Récupère l'ID du patient depuis le JWT
            String keycloakId = jwt.getSubject();
            User user = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (user.getRole() != be.he2b.healthsec.medical_records.model.UserType.PATIENT) {
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

            return ResponseEntity.ok(Map.of("message", msg));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Récupère la liste des médecins associés au patient actuel.
     */
    @GetMapping("/my-doctors")
    public ResponseEntity<?> getMyDoctors(@AuthenticationPrincipal Jwt jwt) {
        try {
            String keycloakId = jwt.getSubject();
            User user = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (user.getRole() != be.he2b.healthsec.medical_records.model.UserType.PATIENT) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only patients can view their doctors"));
            }

            List<UUID> doctorIds = patientDoctorService.getPatientDoctors(user.getId());
            return ResponseEntity.ok(Map.of("doctorIds", doctorIds));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Récupère la liste des patients du médecin actuellement authentifié.
     * Retourne les IDs des patients + données chiffrées (jamais déchiffré côté serveur).
     */
    @GetMapping("/my-patients")
    public ResponseEntity<?> getMyPatients(@AuthenticationPrincipal Jwt jwt) {
        try {
            String keycloakId = jwt.getSubject();
            User user = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (user.getRole() != be.he2b.healthsec.medical_records.model.UserType.DOCTOR) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only doctors can view their patients"));
            }

            var patients = patientDoctorService.getDoctorPatients(user.getId());
            return ResponseEntity.ok(Map.of("patients", patients));
        } catch (IllegalArgumentException e) {
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
        try {
            String keycloakId = jwt.getSubject();
            User user = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (user.getRole() != be.he2b.healthsec.medical_records.model.UserType.DOCTOR) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only doctors can view patient data"));
            }

            UUID doctorId = user.getId();
            UUID patientUuid = UUID.fromString(patientId);

            var patientData = patientDoctorService.getPatientEncryptedData(doctorId, patientUuid);
            return ResponseEntity.ok(patientData);
        } catch (IllegalArgumentException e) {
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
        try {
            String keycloakId = jwt.getSubject();
            User user = userService.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
            
            if (user.getRole() != be.he2b.healthsec.medical_records.model.UserType.PATIENT) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only patients can remove doctors"));
            }

            UUID patientId = user.getId();
            UUID doctorUuid = UUID.fromString(doctorId);

            patientDoctorService.removeDoctorFromPatient(patientId, doctorUuid);
            return ResponseEntity.ok(Map.of("message", "Doctor removed successfully"));
        } catch (IllegalArgumentException e) {
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

