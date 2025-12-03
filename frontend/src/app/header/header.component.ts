import { Component } from '@angular/core';
import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent {

  constructor(public auth: AuthService) {}

  get username() {
    return this.auth.username;
  }

  get email() {
    return this.auth.email;
  }

  get firstName() {
    return this.auth.firstName;
  }

  get lastName() {
    return this.auth.lastName;
  }

  get role(){
    return "Doctor"
  }

  logout() {
    this.auth.logout();
  }
}
