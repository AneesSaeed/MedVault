package be.he2b.healthsec.medical_records.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import be.he2b.healthsec.medical_records.dto.CreatePendingMedicalFileDTO;
import be.he2b.healthsec.medical_records.dto.PendingMedicalFileInfoDTO;
import be.he2b.healthsec.medical_records.logging.LoggingService;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.repository.UserRepository;
import be.he2b.healthsec.medical_records.security.JwtRoles;
import be.he2b.healthsec.medical_records.service.MedicalFileRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pending medical file request API (doctor -> patient workflow).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Doctors create a request for a patient to upload/confirm a file.</li>
 *   <li>Patients list their pending requests.</li>
 *   <li>Patients delete (reject) a pending request.</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class MedicalFileRequestController {

    private final MedicalFileRequestService service;
    private final UserRepository userRepository;
    private final LoggingService logger;

    /**
     * Doctor: creates a pending medical file request for a patient.
     */
    @PostMapping("/patient/{patientId}/file-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> createRequest(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("patientId") UUID patientId,
            @Valid @RequestBody CreatePendingMedicalFileDTO dto
    ) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("POST", "/api/patient/" + patientId + "/file-requests", keycloakId);

        if (!JwtRoles.hasRealmRole(jwt, "DOCTOR")) {
            throw new IllegalArgumentException("Only doctors can create file requests");
        }

        User caller = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String id = service.createRequest(patientId, keycloakId, dto);

        logger.logAction("MEDICAL_FILE_REQUEST_CREATED", keycloakId, Map.of(
                "patientId", patientId.toString(),
                "requestId", id,
                "doctorId", caller.getId().toString()));

        return ResponseEntity.ok(id);
    }

    /**
     * Patient: lists their own pending file requests.
     */
    @GetMapping("/patient/me/file-requests")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PendingMedicalFileInfoDTO>> listMyRequests(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("GET", "/api/patient/me/file-requests", keycloakId);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!JwtRoles.hasRealmRole(jwt, "PATIENT")) {
            logger.logSecurityEvent("UNAUTHORIZED_FILE_REQUEST_LIST", keycloakId, "MEDIUM", Map.of(
                    "effectiveRole", String.valueOf(JwtRoles.effectiveRole(jwt))));
            return ResponseEntity.status(403).build();
        }

        List<PendingMedicalFileInfoDTO> items = service.listForPatient(user.getId());

        logger.info("Patient listed pending file requests", Map.of(
                "patientId", user.getId().toString(),
                "count", items.size()));

        return ResponseEntity.ok(items);
    }

    /**
     * Patient: deletes (rejects) a pending request.
     */
    @DeleteMapping("/patient/file-requests/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteRequest(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable("id") UUID requestId
    ) {
        String keycloakId = jwt.getSubject();
        logger.logApiRequest("DELETE", "/api/patient/file-requests/" + requestId, keycloakId);

        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!JwtRoles.hasRealmRole(jwt, "PATIENT")) {
            logger.logSecurityEvent("UNAUTHORIZED_FILE_REQUEST_DELETE", keycloakId, "MEDIUM", Map.of(
                    "requestId", requestId.toString(),
                    "effectiveRole", String.valueOf(JwtRoles.effectiveRole(jwt))));
            return ResponseEntity.status(403).build();
        }

        service.deleteRequest(requestId, user.getId());

        logger.logAction("MEDICAL_FILE_REQUEST_DELETED", keycloakId, Map.of(
                "requestId", requestId.toString(),
                "patientId", user.getId().toString()));
                
        return ResponseEntity.noContent().build();
    }
}
