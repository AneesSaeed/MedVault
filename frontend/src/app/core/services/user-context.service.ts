import { Injectable } from '@angular/core';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { UserService } from './user.service';
import { AuthService, AppRole } from './auth.service';

@Injectable({ providedIn: 'root' })
export class UserContextService {
  // Source of truth = token
  get role(): AppRole | null {
    return this.auth.userRole;
  }

  // DB UUID (needed for backend relations)
  userId: string | null = null;

  constructor(
    private userService: UserService,
    private auth: AuthService
  ) {}

  loadUserContext$(): Observable<{ userId: string }> {
    return this.userService.getMe().pipe(
      tap((data) => {
        this.userId = data?.userId ?? null;
      })
    );
  }
}
