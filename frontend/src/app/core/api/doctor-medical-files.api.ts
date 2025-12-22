import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MedicalFile } from '../models/medical-file.model';

@Injectable({ providedIn: 'root' })
export class DoctorMedicalFilesApi {
  private readonly baseUrl = 'https://localhost/api/doctor/medical-files';

  constructor(private http: HttpClient) {}

  listForPatient(patientId: string): Observable<{ files: MedicalFile[] }> {
    return this.http.get<{ files: MedicalFile[] }>(`${this.baseUrl}/patient/${patientId}`);
  }

  downloadForPatient(patientId: string, fileId: string) {
    return this.http.get(`${this.baseUrl}/patient/${patientId}/${fileId}/download`, { responseType: 'blob' });
  }
}
