export interface MedicalFile {
  id: string;
  fileNameEncBase64: string;
  uploadDateEncBase64: string;
  sizeBytes: number;
  wrappedFileKeyEncBase64: string;
}
