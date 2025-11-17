// app.ts → Le grand composant racine
// Ce fichier correspond à App.vue dans Vue.
// C’est le point où toute l’application se rassemble et démarre.

import { Component, OnInit } from '@angular/core';
import { ApiService } from './services/api'

@Component({
  selector: 'app-root',
  standalone: true,
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit{
  message = '';

  // Le service ApiService est injecté automatiquement par Angular.
  constructor(private api: ApiService) {}

  // ngOnInit vient de l’interface OnInit importée ci-dessus.
  // Cette méthode est appelée automatiquement par Angular
  // juste après la création du composant, au moment où il "s'initialise".
  // ngOnInit() ≈ onMounted()
  ngOnInit(): void {

      // On s'abonne (subscribe) au résultat de getHello()
      this.api.getHello().subscribe(data => {
        // Quand la réponse arrive, on met à jour la variable message.
        this.message = data;
      })
  }
}
