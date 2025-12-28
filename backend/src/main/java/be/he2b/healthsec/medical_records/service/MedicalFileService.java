package be.he2b.healthsec.medical_records.service;

import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import be.he2b.healthsec.medical_records.dto.MedicalFileInfoDTO;
import be.he2b.healthsec.medical_records.dto.ShareFileKeyDTO;
import be.he2b.healthsec.medical_records.model.MedicalFile;
import be.he2b.healthsec.medical_records.model.MedicalFileKey;
import be.he2b.healthsec.medical_records.model.MedicalFileKeyId;
import be.he2b.healthsec.medical_records.model.MedicalRecord;
import be.he2b.healthsec.medical_records.model.Patient;
import be.he2b.healthsec.medical_records.model.PatientDoctorId;
import be.he2b.healthsec.medical_records.model.User;
import be.he2b.healthsec.medical_records.repository.MedicalFileKeyRepository;
import be.he2b.healthsec.medical_records.repository.MedicalFileRepository;
import be.he2b.healthsec.medical_records.repository.MedicalRecordRepository;
import be.he2b.healthsec.medical_records.repository.PatientDoctorRepository;
import be.he2b.healthsec.medical_records.repository.PatientRepository;
import be.he2b.healthsec.medical_records.repository.UserRepository;
import be.he2b.healthsec.medical_records.logging.LoggingService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MedicalFileService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalFileKeyRepository medicalFileKeyRepository;
    private final MedicalFileRepository medicalFileRepository;
    private final PatientRepository patientRepository;
    private final PatientDoctorRepository patientDoctorRepository;
    private final UserRepository userRepository;
    private final LoggingService logger;

    // ------------------------------------------------------------
    // PATIENT: list own files (must return wrapped key for PATIENT)
    // ------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<MedicalFileInfoDTO> listPatientFiles(UUID patientId) {
        logger.debug("Listing files for patient", java.util.Map.of(
            "patientId", patientId
        ));
        // MedicalRecord is created on first file upload, so return empty list if none exists yet
        MedicalRecord record = medicalRecordRepository.findById(patientId)
            .orElse(null);
        
        if (record == null) {
            logger.debug("No medical record found for patient", java.util.Map.of(
                "patientId", patientId
            ));
            return List.of(); // No medical record yet = no files uploaded
        }

        List<MedicalFileInfoDTO> files = medicalFileRepository.findByMedicalRecordId(record.getId()).stream()
            .map(f -> toInfoDtoForRecipient(f, patientId))
            .collect(Collectors.toList());
        logger.info("Found files for patient", java.util.Map.of(
            "count", files.size(),
            "patientId", patientId
        ));
        return files;
    }

    // ------------------------------------------------------------
    // DOCTOR: list files of a patient (must return wrapped key for DOCTOR)
    // ------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<MedicalFileInfoDTO> listFilesForDoctor(UUID doctorId, UUID patientId) {
        logger.debug("Doctor listing files for patient", java.util.Map.of(
            "doctorId", doctorId,
            "patientId", patientId
        ));
        // must be appointed
        PatientDoctorId relId = new PatientDoctorId(patientId, doctorId);
        if (!patientDoctorRepository.existsById(relId)) {
            logger.warn("Doctor does not have access to patient", java.util.Map.of(
                "doctorId", doctorId,
                "patientId", patientId
            ));
            throw new IllegalArgumentException("Doctor does not have access to this patient");
        }

        // MedicalRecord is created on first file upload, so return empty list if none exists yet
        MedicalRecord record = medicalRecordRepository.findById(patientId)
            .orElse(null);
        
        if (record == null) {
            logger.debug("No medical record found for patient", java.util.Map.of(
                "patientId", patientId
            ));
            return List.of(); // No medical record yet = no files uploaded
        }

        List<MedicalFileInfoDTO> files = medicalFileRepository.findByMedicalRecordId(record.getId()).stream()
            .map(f -> toInfoDtoForRecipient(f, doctorId))
            .collect(Collectors.toList());
        logger.info("Doctor found files for patient", java.util.Map.of(
            "doctorId", doctorId,
            "count", files.size(),
            "patientId", patientId
        ));
        return files;
    }

    // ------------------------------------------------------------
    // PATIENT: share file keys with a doctor (batch)
    // ------------------------------------------------------------
    @Transactional
    public void shareFileKeysWithDoctor(UUID patientId, UUID doctorId, List<ShareFileKeyDTO> items) {
        logger.debug("Sharing file keys with doctor", java.util.Map.of(
            "count", items.size(),
            "patientId", patientId,
            "doctorId", doctorId
        ));
        
        // must be appointed
        PatientDoctorId relId = new PatientDoctorId(patientId, doctorId);
        if (!patientDoctorRepository.existsById(relId)) {
            logger.warn("Doctor is not associated with patient", java.util.Map.of(
                "doctorId", doctorId,
                "patientId", patientId
            ));
            throw new IllegalArgumentException("Doctor is not associated with this patient");
        }

        MedicalRecord record = medicalRecordRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Medical record not found"));

        User doctorUser = userRepository.findById(doctorId)
            .orElseThrow(() -> new IllegalArgumentException("Doctor user not found"));

        for (ShareFileKeyDTO it : items) {
            UUID fileId = UUID.fromString(it.getFileId());

            MedicalFile file = medicalFileRepository.findByIdAndMedicalRecordId(fileId, record.getId())
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

            byte[] wrapped = Base64.getDecoder().decode(it.getWrappedKeyForDoctorBase64());

            MedicalFileKeyId keyId = new MedicalFileKeyId(file.getId(), doctorId);

            MedicalFileKey row = medicalFileKeyRepository.findById(keyId).orElse(
                MedicalFileKey.builder()
                    .id(keyId)
                    .file(file)
                    .recipientUser(doctorUser)
                    .build()
            );

            row.setWrappedFileKeyEnc(wrapped);
            medicalFileKeyRepository.save(row);
        }
        logger.info("Successfully shared file keys with doctor", java.util.Map.of(
            "count", items.size(),
            "patientId", patientId,
            "doctorId", doctorId
        ));
    }


    @Transactional
    public UUID uploadPatientFile(
        UUID patientId,
        String fileNameEncBase64,
        String uploadDateEncBase64,
        MultipartFile encryptedFile,
        String wrappedKeyForPatientBase64
    ) {
        logger.debug("Uploading file for patient", java.util.Map.of(
            "patientId", patientId,
            "sizeBytes", encryptedFile.getSize()
        ));
        
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Patient not found"));

        MedicalRecord record = medicalRecordRepository.findById(patientId).orElseGet(() -> {
            logger.debug("Creating new medical record for patient", java.util.Map.of(
                "patientId", patientId
            ));
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
            logger.error("Failed to read uploaded file", e, java.util.Map.of(
                "patientId", patientId
            ));
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
        logger.info("File uploaded successfully for patient", java.util.Map.of(
            "patientId", patientId,
            "fileId", mf.getId(),
            "sizeBytes", contentEnc.length
        ));

        return mf.getId();
    }

    @Transactional
    public void overwritePatientFile(
        UUID patientId,
        UUID fileId,
        String uploadDateEncBase64,
        MultipartFile encryptedFile
    ) {
        logger.debug("Overwriting file for patient", java.util.Map.of(
            "fileId", fileId,
            "patientId", patientId,
            "sizeBytes", encryptedFile.getSize()
        ));
        
        MedicalRecord record = medicalRecordRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Medical record not found"));

        MedicalFile existing = medicalFileRepository.findByIdAndMedicalRecordId(fileId, record.getId())
            .orElseThrow(() -> new IllegalArgumentException("File not found"));

        byte[] uploadDateEnc = Base64.getDecoder().decode(uploadDateEncBase64);

        byte[] contentEnc;
        try {
            contentEnc = encryptedFile.getBytes();
        } catch (Exception e) {
            logger.error("Failed to read uploaded file during overwrite", e, java.util.Map.of(
                "fileId", fileId,
                "patientId", patientId
            ));
            throw new IllegalArgumentException("Failed to read uploaded file");
        }

        existing.setUploadDateEnc(uploadDateEnc);
        existing.setContentEnc(contentEnc);

        medicalFileRepository.save(existing);
        logger.info("File overwritten successfully for patient", java.util.Map.of(
            "fileId", fileId,
            "patientId", patientId,
            "sizeBytes", contentEnc.length
        ));
        // Note: per-file key does not change on overwrite (same wrapped key stays valid)
    }

    @Transactional
    public void deletePatientFile(UUID patientId, UUID fileId) {
        logger.debug("Deleting file for patient", java.util.Map.of(
            "fileId", fileId,
            "patientId", patientId
        ));
        
        MedicalRecord record = medicalRecordRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Medical record not found"));

        MedicalFile existing = medicalFileRepository.findByIdAndMedicalRecordId(fileId, record.getId())
            .orElseThrow(() -> new IllegalArgumentException("File not found"));

        // Remove wrapped keys first (avoid FK issues, and ensures no stale access)
        medicalFileKeyRepository.deleteByIdFileId(fileId);
        logger.debug("Deleted all wrapped keys for file", java.util.Map.of(
            "fileId", fileId
        ));

        medicalFileRepository.delete(existing);
        logger.info("File deleted successfully for patient", java.util.Map.of(
            "fileId", fileId,
            "patientId", patientId
        ));
    }

    @Transactional(readOnly = true)
    public byte[] getEncryptedFileContent(UUID patientId, UUID fileId) {
        logger.debug("Getting encrypted content for file of patient", java.util.Map.of(
            "fileId", fileId,
            "patientId", patientId
        ));
        
        MedicalRecord record = medicalRecordRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Medical record not found"));

        MedicalFile existing = medicalFileRepository.findByIdAndMedicalRecordId(fileId, record.getId())
            .orElseThrow(() -> new IllegalArgumentException("File not found"));

        logger.info("Retrieved encrypted file content", java.util.Map.of(
            "fileId", fileId,
            "sizeBytes", existing.getContentEnc().length
        ));
        return existing.getContentEnc();
    }

    // ------------------------------------------------------------
    // Shared helper
    // ------------------------------------------------------------
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
