import { Component, inject } from '@angular/core';
import { AuthService } from '../core/services/auth.service';
import { UserContextService } from '../core/services/user-context.service';

import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
  standalone: true,
  imports: [CommonModule, RouterModule]
})
export class HeaderComponent {
  public readonly auth = inject(AuthService);
  public readonly userContext = inject(UserContextService);

  get username() {
    return this.auth.username;
  }

  get email() {
    return this.auth.email; // token
  }

  get firstName() {
    return this.userContext.firstName; // db
  }

  get lastName() {
    return this.userContext.lastName; // db
  }

  get role() {
    return this.userContext.role ?? 'Loading...';
  }

  logout() {
    this.auth.logout();
  }
}
