import { Injectable } from '@angular/core';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { UserService } from './user.service';

@Injectable({ providedIn: 'root' })
export class UserContextService {
  role: string | null = null;
  userId: string | null = null;

  constructor(private userService: UserService) {}

  loadUserContext$(): Observable<any> {
    return this.userService.getMe().pipe(
      tap((data) => {
        if (data) {
          this.role = data.role;
          this.userId = data.userId;
        } else {
          this.role = null;
          this.userId = null;
        }
      })
    );
  }
}
