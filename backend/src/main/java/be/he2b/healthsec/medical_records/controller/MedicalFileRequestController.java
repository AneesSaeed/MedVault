package be.he2b.healthsec.medical_records.controller;

import be.he2b.healthsec.medical_records.dto.CreatePendingMedicalFileDTO;
import be.he2b.healthsec.medical_records.dto.PendingMedicalFileInfoDTO;
import be.he2b.healthsec.medical_records.logging.LoggingService;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MedicalFileRequestController {

    private final MedicalFileRequestService service;
    private final UserRepository userRepository;
    private final LoggingService logger;

    /**
     * DOCTOR: créer une demande pour un patient.
     */
    @PostMapping("/patient/{patientId}/file-requests")
    public ResponseEntity<String> createRequest(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable("patientId") UUID patientId,
                                                @RequestBody CreatePendingMedicalFileDTO dto) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("POST", "/api/patient/" + patientId + "/file-requests", keycloakId);
        
        User caller = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (caller.getRole() != UserType.DOCTOR) {
            logger.logSecurityEvent("UNAUTHORIZED_FILE_REQUEST_CREATION", keycloakId, "MEDIUM", Map.of(
                "patientId", patientId.toString(),
                "userRole", caller.getRole().toString()
            ));
            return ResponseEntity.status(403).body("Only doctors can create file requests");
        }

        String id = service.createRequest(patientId, keycloakId, dto);
        logger.logAction("MEDICAL_FILE_REQUEST_CREATED", keycloakId, Map.of(
            "patientId", patientId.toString(),
            "requestId", id,
            "doctorId", caller.getId().toString()
        ));
        return ResponseEntity.ok(id);
    }

    /**
     * PATIENT: lister ses demandes en attente.
     */
    @GetMapping("/patient/me/file-requests")
    public ResponseEntity<List<PendingMedicalFileInfoDTO>> listMyRequests(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient/me/file-requests", keycloakId);
        
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getRole() != UserType.PATIENT) {
            logger.logSecurityEvent("UNAUTHORIZED_FILE_REQUEST_LIST", keycloakId, "MEDIUM", Map.of(
                "userRole", user.getRole().toString()
            ));
            return ResponseEntity.status(403).build();
        }
        List<PendingMedicalFileInfoDTO> items = service.listForPatient(user.getId());
        logger.info("Patient listed pending file requests", Map.of(
            "patientId", user.getId().toString(),
            "count", items.size()
        ));
        return ResponseEntity.ok(items);
    }

    /**
     * PATIENT: rejeter (supprimer) une demande.
     */
    @DeleteMapping("/patient/file-requests/{id}")
    public ResponseEntity<Void> deleteRequest(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable("id") UUID requestId) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("DELETE", "/api/patient/file-requests/" + requestId, keycloakId);
        
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getRole() != UserType.PATIENT) {
            logger.logSecurityEvent("UNAUTHORIZED_FILE_REQUEST_DELETE", keycloakId, "MEDIUM", Map.of(
                "requestId", requestId.toString(),
                "userRole", user.getRole().toString()
            ));
            return ResponseEntity.status(403).build();
        }
        service.deleteRequest(requestId, user.getId());
        logger.logAction("MEDICAL_FILE_REQUEST_DELETED", keycloakId, Map.of(
            "requestId", requestId.toString(),
            "patientId", user.getId().toString()
        ));
        return ResponseEntity.noContent().build();
    }
}
