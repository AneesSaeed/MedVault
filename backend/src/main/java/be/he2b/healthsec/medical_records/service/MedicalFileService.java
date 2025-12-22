package be.he2b.healthsec.medical_records.service;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import be.he2b.healthsec.medical_records.dto.MedicalFileInfoDTO;
import be.he2b.healthsec.medical_records.model.MedicalFile;
import be.he2b.healthsec.medical_records.model.MedicalFileKey;
import be.he2b.healthsec.medical_records.model.MedicalFileKeyId;
import be.he2b.healthsec.medical_records.model.MedicalRecord;
import be.he2b.healthsec.medical_records.model.Patient;
import be.he2b.healthsec.medical_records.repository.MedicalFileKeyRepository;
import be.he2b.healthsec.medical_records.repository.MedicalFileRepository;
import be.he2b.healthsec.medical_records.repository.MedicalRecordRepository;
import be.he2b.healthsec.medical_records.repository.PatientRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MedicalFileService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalFileKeyRepository medicalFileKeyRepository;
    private final MedicalFileRepository medicalFileRepository;
    private final PatientRepository patientRepository;

    @Transactional(readOnly = true)
    public List<MedicalFileInfoDTO> listPatientFiles(UUID patientId) {
        MedicalRecord record = medicalRecordRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Medical record not found"));

        // Ensure patient exists to get patient.user.id for key lookup
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        UUID patientUserId = patient.getUser().getId();

        return medicalFileRepository.findByMedicalRecordId(record.getId()).stream()
            .map(mf -> toInfoDtoForRecipient(mf, patientUserId))
            .collect(Collectors.toList());
    }

    @Transactional
    public UUID uploadPatientFile(
        UUID patientId,
        String fileNameEncBase64,
        String uploadDateEncBase64,
        MultipartFile encryptedFile,
        String wrappedKeyForPatientBase64
    ) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        MedicalRecord record = medicalRecordRepository.findById(patientId).orElseGet(() -> {
            MedicalRecord created = MedicalRecord.builder()
                .patient(patient)
                .build();
            return medicalRecordRepository.save(created);
        });

        byte[] fileNameEnc = Base64.getDecoder().decode(fileNameEncBase64);
        byte[] uploadDateEnc = Base64.getDecoder().decode(uploadDateEncBase64);

        byte[] contentEnc;
        try {
            contentEnc = encryptedFile.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read uploaded file");
        }

        MedicalFile mf = MedicalFile.builder()
            .medicalRecord(record)
            .fileNameEnc(fileNameEnc)
            .uploadDateEnc(uploadDateEnc)
            .contentEnc(contentEnc)
            .build();

        medicalFileRepository.save(mf);

        byte[] wrappedKeyForPatient = Base64.getDecoder().decode(wrappedKeyForPatientBase64);
        UUID recipientUserId = patient.getUser().getId();

        MedicalFileKey keyRow = MedicalFileKey.builder()
            .id(new MedicalFileKeyId(mf.getId(), recipientUserId))
            .file(mf)
            .recipientUser(patient.getUser())
            .wrappedFileKeyEnc(wrappedKeyForPatient)
            .build();

        medicalFileKeyRepository.save(keyRow);

        return mf.getId();
    }

    @Transactional
    public void overwritePatientFile(
        UUID patientId,
        UUID fileId,
        String uploadDateEncBase64,
        MultipartFile encryptedFile
    ) {
        MedicalRecord record = medicalRecordRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Medical record not found"));

        MedicalFile existing = medicalFileRepository.findByIdAndMedicalRecordId(fileId, record.getId())
            .orElseThrow(() -> new IllegalArgumentException("File not found"));

        byte[] uploadDateEnc = Base64.getDecoder().decode(uploadDateEncBase64);

        byte[] contentEnc;
        try {
            contentEnc = encryptedFile.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read uploaded file");
        }

        existing.setUploadDateEnc(uploadDateEnc);
        existing.setContentEnc(contentEnc);

        medicalFileRepository.save(existing);
        // Note: per-file key does not change on overwrite (same wrapped key stays valid)
    }

    @Transactional
    public void deletePatientFile(UUID patientId, UUID fileId) {
        MedicalRecord record = medicalRecordRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Medical record not found"));

        MedicalFile existing = medicalFileRepository.findByIdAndMedicalRecordId(fileId, record.getId())
            .orElseThrow(() -> new IllegalArgumentException("File not found"));

        // Remove wrapped keys first (avoid FK issues, and ensures no stale access)
        medicalFileKeyRepository.deleteByIdFileId(fileId);

        medicalFileRepository.delete(existing);
    }

    @Transactional(readOnly = true)
    public byte[] getEncryptedFileContent(UUID patientId, UUID fileId) {
        MedicalRecord record = medicalRecordRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Medical record not found"));

        MedicalFile existing = medicalFileRepository.findByIdAndMedicalRecordId(fileId, record.getId())
            .orElseThrow(() -> new IllegalArgumentException("File not found"));

        return existing.getContentEnc();
    }

    private MedicalFileInfoDTO toInfoDtoForRecipient(MedicalFile mf, UUID recipientUserId) {
        MedicalFileInfoDTO dto = new MedicalFileInfoDTO();
        dto.setId(mf.getId().toString());
        dto.setFileNameEncBase64(Base64.getEncoder().encodeToString(mf.getFileNameEnc()));
        dto.setUploadDateEncBase64(Base64.getEncoder().encodeToString(mf.getUploadDateEnc()));
        dto.setSizeBytes(mf.getContentEnc() == null ? 0 : mf.getContentEnc().length);

        MedicalFileKey keyRow = medicalFileKeyRepository
            .findByIdFileIdAndIdRecipientUserId(mf.getId(), recipientUserId)
            .orElseThrow(() -> new IllegalArgumentException("Missing wrapped key for recipient"));

        dto.setWrappedFileKeyEncBase64(Base64.getEncoder().encodeToString(keyRow.getWrappedFileKeyEnc()));
        return dto;
    }
}
