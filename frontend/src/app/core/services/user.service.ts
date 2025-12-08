import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";


@Injectable({
  providedIn: 'root'
})
export class UserService {
  private api = 'https://localhost/api'

  constructor(private http: HttpClient) {}

  userExists() {
    return this.http.get<boolean>(`${this.api}/user/exists`);
  }

  getMe() {
    return this.http.get<any>(`${this.api}/user/me`);
  }

  createPatient(payload: any) {
    return this.http.post(`${this.api}/patient`, payload)
  }

  createDoctor(payload: any) {
    return this.http.post(`${this.api}/doctor`, payload)
  }
}
