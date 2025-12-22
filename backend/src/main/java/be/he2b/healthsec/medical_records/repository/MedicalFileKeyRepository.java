package be.he2b.healthsec.medical_records.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import be.he2b.healthsec.medical_records.model.MedicalFileKey;
import be.he2b.healthsec.medical_records.model.MedicalFileKeyId;

public interface MedicalFileKeyRepository extends JpaRepository<MedicalFileKey, MedicalFileKeyId> {
    
    @Modifying
    @Transactional
    @Query("""
        delete from MedicalFileKey k
        where k.recipientUser.id = :doctorUserId
          and k.file.id in (
            select f.id from MedicalFile f
            where f.medicalRecord.id = :patientId
          )
    """)
    int deleteDoctorKeysForPatient(@Param("doctorUserId") UUID doctorUserId,
                                  @Param("patientId") UUID patientId);
                                  
    List<MedicalFileKey> findByIdRecipientUserId(UUID recipientUserId);

    Optional<MedicalFileKey> findByIdFileIdAndIdRecipientUserId(UUID fileId, UUID recipientUserId);

    void deleteByIdRecipientUserId(UUID recipientUserId);

    void deleteByIdRecipientUserIdAndFileMedicalRecordId(UUID recipientUserId, UUID medicalRecordId);
    
    void deleteByIdFileId(UUID fileId);
}
