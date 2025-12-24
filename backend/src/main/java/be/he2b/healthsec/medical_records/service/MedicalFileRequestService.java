package be.he2b.healthsec.medical_records.service;

import be.he2b.healthsec.medical_records.dto.CreatePendingMedicalFileDTO;
import be.he2b.healthsec.medical_records.dto.PendingMedicalFileInfoDTO;
import be.he2b.healthsec.medical_records.model.*;
import be.he2b.healthsec.medical_records.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicalFileRequestService {

    private final PendingMedicalFileRequestRepository pendingRepo;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final PatientDoctorRepository patientDoctorRepository;

    /**
     * Crée une demande d'upload de fichier pour un patient par un docteur.
     */
    @Transactional
    public String createRequest(UUID patientId, String doctorKeycloakId, CreatePendingMedicalFileDTO dto) {
        // Vérifier patient
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        // Résoudre le docteur via le KeycloakId
        User doctorUser = userRepository.findByKeycloakId(doctorKeycloakId)
                .orElseThrow(() -> new IllegalArgumentException("Doctor user not found"));
        Doctor doctor = doctorRepository.findById(doctorUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Doctor not found"));

        // Vérifier la relation PatientDoctor
        PatientDoctorId relId = new PatientDoctorId(patient.getId(), doctor.getId());
        if (!patientDoctorRepository.existsById(relId)) {
            throw new IllegalArgumentException("Doctor does not have access to this patient");
        }

        // Construire l'entité
        PendingMedicalFileRequest req = PendingMedicalFileRequest.builder()
                .id(UUID.randomUUID())
                .patient(patient)
                .uploaderDoctor(doctor)
                .fileNameEnc(safeDecode(dto.getFileNameEncBase64()))
                .contentEnc(requiredDecode(dto.getContentEncBase64()))
                .iv(requiredDecode(dto.getIvBase64()))
                .wrappedTempKeyForPatient(requiredDecode(dto.getWrappedTempKeyForPatientBase64()))
                .mimeTypeEnc(safeDecode(dto.getMimeTypeEncBase64()))
                .createdAt(Instant.now())
                .build();

        pendingRepo.save(req);
        return req.getId().toString();
    }

    /**
     * Liste les demandes pour un patient.
     */
    @Transactional(readOnly = true)
    public List<PendingMedicalFileInfoDTO> listForPatient(UUID patientId) {
        return pendingRepo.findByPatientId(patientId).stream()
                .map(r -> PendingMedicalFileInfoDTO.builder()
                        .id(r.getId().toString())
                        .uploaderDoctorId(r.getUploaderDoctor().getId().toString())
                        .fileNameEncBase64(encode(r.getFileNameEnc()))
                        .contentEncBase64(encode(r.getContentEnc()))
                        .ivBase64(encode(r.getIv()))
                        .wrappedTempKeyForPatientBase64(encode(r.getWrappedTempKeyForPatient()))
                        .mimeTypeEncBase64(encode(r.getMimeTypeEnc()))
                        .createdAt(r.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Supprime une demande appartenant au patient.
     */
    @Transactional
    public void deleteRequest(UUID requestId, UUID patientId) {
        PendingMedicalFileRequest req = pendingRepo.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        if (!req.getPatient().getId().equals(patientId)) {
            throw new IllegalArgumentException("Request does not belong to patient");
        }
        pendingRepo.deleteById(requestId);
    }

    private static byte[] requiredDecode(String base64) {
        if (base64 == null || base64.isEmpty()) {
            throw new IllegalArgumentException("Required Base64 field is missing");
        }
        return Base64.getDecoder().decode(base64);
    }

    private static byte[] safeDecode(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        return Base64.getDecoder().decode(base64);
    }

    private static String encode(byte[] bytes) {
        if (bytes == null) return null;
        return Base64.getEncoder().encodeToString(bytes);
    }
}
