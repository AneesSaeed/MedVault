import { Component } from '@angular/core';
import { AuthService } from '../core/services/auth.service';
import { UserContextService } from '../core/services/user-context.service';

@Component({
    selector: 'app-header',
    templateUrl: './header.component.html',
    styleUrls: ['./header.component.scss'],
    standalone: false
})
export class HeaderComponent {

  constructor(
    public auth: AuthService,
    public userContext: UserContextService
  ) {}

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

  get role() {
    return this.userContext.role ?? 'Loading...';
  }

  logout() {
    this.auth.logout();
  }
}
