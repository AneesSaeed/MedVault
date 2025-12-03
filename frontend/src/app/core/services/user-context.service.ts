import { Injectable } from '@angular/core';
import { UserService } from './user.service';

@Injectable({ providedIn: 'root' })
export class UserContextService {

  role: string | null = null;
  userId: string | null = null;

  constructor(private userService: UserService) {}

  loadUserContext() {
    return this.userService.getMe().subscribe(data => {
      if (data) {
        this.role = data.role;
        this.userId = data.userId;
      }
    });
  }
}
