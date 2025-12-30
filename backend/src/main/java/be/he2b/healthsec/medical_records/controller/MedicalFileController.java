package be.he2b.healthsec.medical_records.controller;

import java.io.IOException;
import java.io.InputStream;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import be.he2b.healthsec.medical_records.dto.MedicalFileInfoDTO;
import be.he2b.healthsec.medical_records.logging.LoggingService;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.security.JwtRoles;
import be.he2b.healthsec.medical_records.service.MedicalFileService;
import be.he2b.healthsec.medical_records.service.UserService;
import be.he2b.healthsec.medical_records.util.EncryptedFileValidator;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/medical-files")
@RequiredArgsConstructor
@Validated
public class MedicalFileController {

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB

    private final MedicalFileService medicalFileService;
    private final UserService userService;
    private final LoggingService logger;
    private final be.he2b.healthsec.medical_records.config.RateLimitService rateLimitService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listMyFiles(@AuthenticationPrincipal Jwt jwt) {
        logger.logApiRequest("GET", "/api/medical-files", jwt.getSubject());
        try {
            UUID patientId = currentPatientIdOrThrow(jwt);
            List<MedicalFileInfoDTO> files = medicalFileService.listPatientFiles(patientId);
            logger.logAction("PATIENT_LISTED_FILES", jwt.getSubject(), Map.of(
                "patientId", patientId.toString(),
                "filesCount", files.size()
            ));
            return ResponseEntity.ok(Map.of("files", files));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to list patient files", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> listFilesForDoctor(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String patientId
    ) {
        logger.logApiRequest("GET", "/api/medical-files/patient/" + patientId, jwt.getSubject());
        
        try {
            UUID doctorId = currentDoctorIdOrThrow(jwt);
            List<MedicalFileInfoDTO> files = medicalFileService.listFilesForDoctor(doctorId, UUID.fromString(patientId));
            logger.logAction("DOCTOR_LISTED_PATIENT_FILES", jwt.getSubject(), Map.of(
                "doctorId", doctorId.toString(),
                "patientId", patientId,
                "filesCount", files.size()
            ));
            return ResponseEntity.ok(Map.of("files", files));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to list files for doctor: " + e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> upload(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam("fileNameEncBase64") String fileNameEncBase64,
        @RequestParam("uploadDateEncBase64") String uploadDateEncBase64,
        @RequestParam String wrappedKeyForPatientBase64,
        @RequestPart("file") MultipartFile file
    ) {
        logger.logApiRequest("POST", "/api/medical-files", jwt.getSubject());
        
        // SECURITY: Rate limiting sur les uploads de fichiers (Question 10 rapport sécurité)
        String userId = jwt.getSubject();
        boolean allowed = rateLimitService.isRequestAllowed(
            userId,
            be.he2b.healthsec.medical_records.config.RateLimitService.RequestType.FILE_UPLOAD
        );
        
        if (!allowed) {
            rateLimitService.recordRequest(userId, 
                be.he2b.healthsec.medical_records.config.RateLimitService.RequestType.FILE_UPLOAD, 
                false
            );
            return ResponseEntity.status(429).body(
                Map.of("error", "Too many requests. Please try again later.")
            );
        }
        
        try {
            UUID patientId = currentPatientIdOrThrow(jwt);
            validateEncryptedUploadOrThrow(file);
            UUID fileId = medicalFileService.uploadPatientFile(
                patientId, 
                fileNameEncBase64, 
                uploadDateEncBase64, 
                file,
                wrappedKeyForPatientBase64
            );
            logger.logAction("MEDICAL_FILE_UPLOADED", jwt.getSubject(), Map.of(
                "patientId", patientId.toString(),
                "fileId", fileId.toString(),
                "fileSize", file.getSize()
            ));
            return ResponseEntity.ok(Map.of("fileId", fileId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to upload medical file", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping(value = "/{fileId}", consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> overwrite(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String fileId,
        @RequestParam("uploadDateEncBase64") String uploadDateEncBase64,
        @RequestPart("file") MultipartFile file
    ) {
        logger.logApiRequest("PUT", "/api/medical-files/" + fileId, jwt.getSubject());
        
        try {
            UUID patientId = currentPatientIdOrThrow(jwt);
            validateEncryptedUploadOrThrow(file);
            medicalFileService.overwritePatientFile(
                patientId, 
                UUID.fromString(fileId), 
                uploadDateEncBase64, 
                file
            );
            logger.logAction("MEDICAL_FILE_OVERWRITTEN", jwt.getSubject(), Map.of(
                "patientId", patientId.toString(),
                "fileId", fileId,
                "fileSize", file.getSize()
            ));
            return ResponseEntity.ok(Map.of("message", "File overwritten"));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to overwrite medical file: " + e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{fileId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String fileId
    ) {
        logger.logApiRequest("DELETE", "/api/medical-files/" + fileId, jwt.getSubject());
        try {
            UUID patientId = currentPatientIdOrThrow(jwt);
            medicalFileService.deletePatientFile(patientId, UUID.fromString(fileId));
            logger.logAction("MEDICAL_FILE_DELETED", jwt.getSubject(), Map.of(
                "patientId", patientId.toString(),
                "fileId", fileId
            ));
            return ResponseEntity.ok(Map.of("message", "File deleted"));
        } catch (IllegalArgumentException e) {
            logger.error("Failed to delete medical file", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{fileId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> downloadMyFile(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String fileId
    ) {
        logger.logApiRequest("GET", "/api/medical-files/" + fileId + "/download", jwt.getSubject());
        try {
            UUID patientId = currentPatientIdOrThrow(jwt);
            byte[] contentEnc = medicalFileService.getEncryptedFileContent(patientId, UUID.fromString(fileId));
            logger.logAction("MEDICAL_FILE_DOWNLOADED", jwt.getSubject(), Map.of(
                "patientId", patientId.toString(),
                "fileId", fileId,
                "contentSize", contentEnc.length
            ));
            return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .header("Content-Disposition", "attachment; filename=\"content.enc\"")
                .body(contentEnc);

        } catch (IllegalArgumentException e) {
            logger.error("Failed to download medical file", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/patient/{patientId}/{fileId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> downloadFileForDoctor(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String patientId,
        @PathVariable String fileId
    ) {
        try {
            UUID doctorId = currentDoctorIdOrThrow(jwt);

            // only checks access; content is still encrypted
            medicalFileService.listFilesForDoctor(doctorId, UUID.fromString(patientId));

            byte[] contentEnc = medicalFileService.getEncryptedFileContent(UUID.fromString(patientId), UUID.fromString(fileId));

            return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .header("Content-Disposition", "attachment; filename=\"content.enc\"")
                .body(contentEnc);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    

    @PostMapping("/share/doctor/{doctorId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> shareKeysWithDoctor(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String doctorId,
        @RequestBody List<be.he2b.healthsec.medical_records.dto.ShareFileKeyDTO> items
    ) {
        try {
            UUID patientId = currentPatientIdOrThrow(jwt);
            medicalFileService.shareFileKeysWithDoctor(patientId, UUID.fromString(doctorId), items);
            return ResponseEntity.ok(Map.of("message", "Keys shared"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ---- helpers ----
    private void validateEncryptedUploadOrThrow(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File too large (max 10 MB)");
        }
        
        // SECURITY: Tous les fichiers DOIVENT être chiffrés côté client avant upload
        // Référence: SECURITY_CHECKLIST.md Section 1 - Confidentiality (zero-trust server)
        // Le backend ne fait pas confiance au serveur, donc tous les fichiers doivent être chiffrés
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        
        // EXIGENCE: Tous les fichiers doivent avoir l'extension .enc (fichiers chiffrés uniquement)
        if (fileName == null || !fileName.endsWith(".enc")) {
            logger.logSecurityEvent(
                "UNENCRYPTED_FILE_REJECTED",
                "system",
                "HIGH",
                Map.of(
                    "fileName", fileName != null ? fileName : "null",
                    "contentType", contentType != null ? contentType : "unknown",
                    "fileSize", file.getSize(),
                    "reason", "File must be encrypted client-side before upload (extension .enc required)"
                )
            );
            throw new IllegalArgumentException(
                "Tous les fichiers doivent être chiffrés côté client avant l'upload. " +
                "Extension .enc requise. Le serveur ne fait pas confiance aux fichiers non-chiffrés."
            );
        }
        
        // Valider la structure chiffrée (IV 12 bytes + ciphertext)
        try (InputStream inputStream = file.getInputStream()) {
            boolean isValidEncrypted = EncryptedFileValidator.validateEncryptedFile(inputStream);
            
            if (!isValidEncrypted) {
                // Log détaillé pour débogage
                logger.logSecurityEvent(
                    "INVALID_ENCRYPTED_FILE_STRUCTURE",
                    "system",
                    "HIGH",
                    Map.of(
                        "fileName", fileName,
                        "contentType", contentType != null ? contentType : "unknown",
                        "fileSize", file.getSize(),
                        "reason", "File does not match expected encrypted structure (IV + ciphertext)",
                        "minSize", "13 bytes (IV 12 bytes + 1 byte minimum)"
                    )
                );
                logger.error("Encrypted file validation failed", null, Map.of(
                    "fileName", fileName,
                    "fileSize", file.getSize()
                ));
                throw new IllegalArgumentException(EncryptedFileValidator.getValidationErrorMessage());
            }
        } catch (IOException e) {
            logger.error("Failed to validate encrypted file structure", e, Map.of(
                "fileName", fileName,
                "fileSize", file.getSize()
            ));
            throw new IllegalArgumentException("Failed to read file for validation: " + e.getMessage());
        }
    }

    private UUID currentPatientIdOrThrow(Jwt jwt) {
        if (!JwtRoles.hasRealmRole(jwt, "PATIENT")) {
            throw new IllegalArgumentException("Only patients can manage medical files");
        }
        String keycloakId = jwt.getSubject();
        User user = userService.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getId();
    }

    private UUID currentDoctorIdOrThrow(Jwt jwt) {
        if (!JwtRoles.hasRealmRole(jwt, "DOCTOR")) {
            throw new IllegalArgumentException("Only doctors can access patient files");
        }
        String keycloakId = jwt.getSubject();
        User user = userService.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getId();
    }
}
