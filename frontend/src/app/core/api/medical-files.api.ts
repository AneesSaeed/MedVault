import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MedicalFile } from '../models/medical-file.model';

@Injectable({ providedIn: 'root' })
export class MedicalFilesApi {
  private readonly baseUrl = 'https://localhost/api/medical-files';

  constructor(private http: HttpClient) {}

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
}
