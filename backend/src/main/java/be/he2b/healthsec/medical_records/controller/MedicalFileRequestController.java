package be.he2b.healthsec.medical_records.controller;

import be.he2b.healthsec.medical_records.dto.CreatePendingMedicalFileDTO;
import be.he2b.healthsec.medical_records.dto.PendingMedicalFileInfoDTO;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.model.UserType;
import be.he2b.healthsec.medical_records.repository.UserRepository;
import be.he2b.healthsec.medical_records.service.MedicalFileRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MedicalFileRequestController {

    private final MedicalFileRequestService service;
    private final UserRepository userRepository;

    /**
     * DOCTOR: créer une demande pour un patient.
     */
    @PostMapping("/patient/{patientId}/file-requests")
    public ResponseEntity<String> createRequest(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable("patientId") UUID patientId,
                                                @RequestBody CreatePendingMedicalFileDTO dto) {
        String keycloakId = jwt.getSubject();
        // Vérifier que l'appelant est un docteur
        User caller = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (caller.getRole() != UserType.DOCTOR) {
            return ResponseEntity.status(403).body("Only doctors can create file requests");
        }

        String id = service.createRequest(patientId, keycloakId, dto);
        return ResponseEntity.ok(id);
    }

    /**
     * PATIENT: lister ses demandes en attente.
     */
    @GetMapping("/patient/me/file-requests")
    public ResponseEntity<List<PendingMedicalFileInfoDTO>> listMyRequests(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getRole() != UserType.PATIENT) {
            return ResponseEntity.status(403).build();
        }
        List<PendingMedicalFileInfoDTO> items = service.listForPatient(user.getId());
        return ResponseEntity.ok(items);
    }

    /**
     * PATIENT: rejeter (supprimer) une demande.
     */
    @DeleteMapping("/patient/file-requests/{id}")
    public ResponseEntity<Void> deleteRequest(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable("id") UUID requestId) {
        String keycloakId = jwt.getSubject();
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getRole() != UserType.PATIENT) {
            return ResponseEntity.status(403).build();
        }
        service.deleteRequest(requestId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
