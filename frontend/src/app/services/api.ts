// Cette classe sert de service pour communiquer avec le backend.
// Dans Vue, l’équivalent serait plus direct : il suffirait d’appeler axios.get()
// sans passer par un service structuré comme ici.

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class ApiService {

  private baseUrl = 'http://localhost:8081/api';

  constructor(private http: HttpClient) {}

  // cet method envoie une requête GET vers l’endpoint /api/public/hello
  getHello(): Observable<string> {
    return this.http.get(`${this.baseUrl}/public/hello`, {
      responseType: 'text'
    })
  }
}
