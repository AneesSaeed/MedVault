package be.he2b.healthsec.medical_records.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import be.he2b.healthsec.medical_records.dto.MedicalFileInfoDTO;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.model.UserType;
import be.he2b.healthsec.medical_records.service.MedicalFileService;
import be.he2b.healthsec.medical_records.service.UserService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/medical-files")
@RequiredArgsConstructor
public class MedicalFileController {

    private static final long MAX_BYTES = 10L * 1024 * 1024; // 10 MB

    private final MedicalFileService medicalFileService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> listMyFiles(@AuthenticationPrincipal Jwt jwt) {
        try {
            UUID patientId = currentPatientIdOrThrow(jwt);
            List<MedicalFileInfoDTO> files = medicalFileService.listPatientFiles(patientId);
            return ResponseEntity.ok(Map.of("files", files));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> upload(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam("fileNameEncBase64") String fileNameEncBase64,
        @RequestParam("uploadDateEncBase64") String uploadDateEncBase64,
        @RequestParam String wrappedKeyForPatientBase64,
        @RequestPart("file") MultipartFile file
    ) {
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
            return ResponseEntity.ok(Map.of("fileId", fileId.toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping(value = "/{fileId}", consumes = "multipart/form-data")
    public ResponseEntity<?> overwrite(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String fileId,
        @RequestParam("uploadDateEncBase64") String uploadDateEncBase64,
        @RequestPart("file") MultipartFile file
    ) {
        try {
            UUID patientId = currentPatientIdOrThrow(jwt);
            validateEncryptedUploadOrThrow(file);
            medicalFileService.overwritePatientFile(
                patientId, 
                UUID.fromString(fileId), 
                uploadDateEncBase64, 
                file
            );
            return ResponseEntity.ok(Map.of("message", "File overwritten"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> delete(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String fileId
    ) {
        try {
            UUID patientId = currentPatientIdOrThrow(jwt);
            medicalFileService.deletePatientFile(patientId, UUID.fromString(fileId));
            return ResponseEntity.ok(Map.of("message", "File deleted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<?> downloadMyFile(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String fileId
    ) {
        try {
            UUID patientId = currentPatientIdOrThrow(jwt);
            byte[] contentEnc = medicalFileService.getEncryptedFileContent(patientId, UUID.fromString(fileId));

            return ResponseEntity.ok()
                .header("Content-Type", "application/octet-stream")
                .header("Content-Disposition", "attachment; filename=\"content.enc\"")
                .body(contentEnc);

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
    }


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
