package be.he2b.healthsec.medical_records.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import be.he2b.healthsec.medical_records.model.PatientSymmetricKey;
import be.he2b.healthsec.medical_records.model.PatientSymmetricKeyId;

@Repository
public interface PatientSymmetricKeyRepository extends JpaRepository<PatientSymmetricKey, PatientSymmetricKeyId> {
    
    /**
     * Récupère la clé symétrique chiffrée d'un patient pour un destinataire spécifique.
     * 
     * @param patientId ID du patient
     * @param recipientUserId ID du destinataire (patient lui-même ou docteur)
     * @return La ligne PatientSymmetricKey, ou null si elle n'existe pas
     */
    @Query("SELECT psk FROM PatientSymmetricKey psk " +
           "WHERE psk.patient.id = :patientId AND psk.recipientUser.id = :recipientUserId")
    PatientSymmetricKey findByPatientAndRecipient(
        @Param("patientId") UUID patientId,
        @Param("recipientUserId") UUID recipientUserId
    );
    
    /**
     * Supprime TOUTES les clés symétriques d'un patient pour un docteur spécifique.
     * Appelé quand un patient retire un docteur.
     * 
     * @param patientId ID du patient
     * @param doctorId ID du docteur à retirer
     */
    @Modifying
    @Query("DELETE FROM PatientSymmetricKey psk " +
           "WHERE psk.patient.id = :patientId AND psk.recipientUser.id = :doctorId")
    void deleteByPatientAndDoctor(
        @Param("patientId") UUID patientId,
        @Param("doctorId") UUID doctorId
    );
}
