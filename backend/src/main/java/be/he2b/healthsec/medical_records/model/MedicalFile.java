package be.he2b.healthsec.medical_records.model;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

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

    @Lob
    @Column(name = "file_name_enc")
    private byte[] fileNameEnc;

    @Lob
    @Column(name = "upload_date_enc")
    private byte[] uploadDateEnc;

    @Lob
    @Column(name = "content_enc")
    private byte[] contentEnc;

}

