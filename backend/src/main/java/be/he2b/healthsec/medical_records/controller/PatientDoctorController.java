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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import be.he2b.healthsec.medical_records.dto.AddDoctorToPatientDTO;
import be.he2b.healthsec.medical_records.dto.DoctorInfoDTO;
import be.he2b.healthsec.medical_records.dto.PatientInfoDTO;
import be.he2b.healthsec.medical_records.model.User;
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

    /**
     * Liste tous les médecins disponibles (pour qu'un patient puisse les rechercher).
     * Retourne les informations en clair (nom, prénom, organisation).
     */
    @GetMapping("/doctors")
    public ResponseEntity<?> listAllDoctors() {
        try {
            List<DoctorInfoDTO> doctors = patientDoctorService.listAllDoctors();
            return ResponseEntity.ok(Map.of("doctors", doctors));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Recherche des patients par nom et/ou prénom.
     * Utilisé par les médecins pour trouver des patients à qui demander l'accès.
     * 
     * @param firstName Prénom (optionnel)
     * @param lastName Nom (optionnel)
     */
    @GetMapping("/patients/search")
    public ResponseEntity<?> searchPatients(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName) {
        try {
            List<PatientInfoDTO> patients = patientDoctorService.searchPatients(firstName, lastName);
            return ResponseEntity.ok(Map.of("patients", patients));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Récupère la clé publique RSA d'un patient.
     * Permet à un médecin de récupérer la clé publique d'un patient
     * pour demander l'accès (si nécessaire).
     */
    @GetMapping("/patient/{patientId}/public-key")
    public ResponseEntity<?> getPatientPublicKey(@PathVariable String patientId) {
        try {
            UUID patientUuid = UUID.fromString(patientId);
            String publicKey = patientDoctorService.getPatientPublicKey(patientUuid);
            return ResponseEntity.ok(Map.of("publicKeyPEM", publicKey));
        } catch (IllegalArgumentException e) {
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
}

