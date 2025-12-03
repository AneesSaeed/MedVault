package be.he2b.healthsec.medical_records.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "medical_files")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MedicalFile {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_record_id", nullable = false)
    private MedicalRecord medicalRecord;

    @Column(name = "file_name_enc", columnDefinition = "bytea")
    private byte[] fileNameEnc;

    @Column(name = "upload_date_enc", columnDefinition = "bytea")
    private byte[] uploadDateEnc;

    @Column(name = "content_enc", columnDefinition = "bytea")
    private byte[] contentEnc;
}

