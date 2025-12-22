package be.he2b.healthsec.medical_records.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import be.he2b.healthsec.medical_records.model.MedicalFileKey;
import be.he2b.healthsec.medical_records.model.MedicalFileKeyId;

public interface MedicalFileKeyRepository extends JpaRepository<MedicalFileKey, MedicalFileKeyId> {

    List<MedicalFileKey> findByIdRecipientUserId(UUID recipientUserId);

    Optional<MedicalFileKey> findByIdFileIdAndIdRecipientUserId(UUID fileId, UUID recipientUserId);

    void deleteByIdRecipientUserId(UUID recipientUserId);

    void deleteByIdRecipientUserIdAndFileMedicalRecordId(UUID recipientUserId, UUID medicalRecordId);
    
    void deleteByIdFileId(UUID fileId);
}
