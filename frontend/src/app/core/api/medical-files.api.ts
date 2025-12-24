import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MedicalFile } from '../models/medical-file.model';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class MedicalFilesApi {
  private readonly baseUrl = `${environment.apiBaseUrl}/medical-files`;

  private http = inject(HttpClient);

  list(): Observable<{ files: MedicalFile[] }> {
    return this.http.get<{ files: MedicalFile[] }>(this.baseUrl);
  }

  upload(form: FormData): Observable<{ fileId: string }> {
    return this.http.post<{ fileId: string }>(this.baseUrl, form);
  }

  overwrite(fileId: string, form: FormData): Observable<{ message: string }> {
    return this.http.put<{ message: string }>(`${this.baseUrl}/${fileId}`, form);
  }

  delete(fileId: string): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/${fileId}`);
  }

  download(fileId: string) {
    return this.http.get(`${this.baseUrl}/${fileId}/download`, { responseType: 'blob' });
  }

  listForDoctor(patientId: string): Observable<{ files: MedicalFile[] }> {
    return this.http.get<{ files: MedicalFile[] }>(`${this.baseUrl}/patient/${patientId}`);
  }

  downloadForDoctor(patientId: string, fileId: string) {
    return this.http.get(`${this.baseUrl}/patient/${patientId}/${fileId}/download`, { responseType: 'blob' });
  }

  shareKeysWithDoctor(doctorId: string, items: { fileId: string; wrappedKeyForDoctorBase64: string }[]) {
    return this.http.post<{ message: string }>(`${this.baseUrl}/share/doctor/${doctorId}`, items);
  }
}
