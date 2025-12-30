import { Injectable, inject } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';

export type AppRole = 'PATIENT' | 'DOCTOR';

interface KeycloakToken {
  sub?: string;
  preferred_username?: string;
  email?: string;
  realm_access?: { roles?: string[] };
  selected_role?: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly keycloak = inject(KeycloakService);

  private get token(): KeycloakToken {
    return (this.keycloak.getKeycloakInstance().tokenParsed as KeycloakToken) ?? {};
  }

  get sub(): string {
    const v = this.token.sub;
    if (!v) throw new Error('Missing Keycloak subject (sub). User not authenticated?');
    return v;
  }

  get username(): string | undefined {
    return this.token.preferred_username;
  }

  // KEEP email in token (as you confirmed)
  get email(): string | undefined {
    return this.token.email;
  }

  /** Realm roles from JWT: realm_access.roles */
  get roles(): string[] {
    const roles = this.token?.realm_access?.roles;
    return Array.isArray(roles) ? roles : [];
  }

  hasRole(role: string): boolean {
    return this.roles.includes(role);
  }

  get selectedRole(): AppRole | null {
    const v = this.token?.selected_role;
    return v === 'PATIENT' || v === 'DOCTOR' ? v : null;
  }

  get userRole(): AppRole | null {
    if (this.hasRole('DOCTOR')) return 'DOCTOR';
    if (this.hasRole('PATIENT')) return 'PATIENT';
    return this.selectedRole;
  }

  async refreshToken(): Promise<void> {
    const kc = this.keycloak.getKeycloakInstance();
    await kc.updateToken(-1);
  }

  logout() {
    return this.keycloak.logout('https://localhost/');
  }
}
