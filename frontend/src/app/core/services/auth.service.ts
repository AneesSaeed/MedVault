import { Injectable } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';

export type AppRole = 'PATIENT' | 'DOCTOR';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  constructor(private keycloak: KeycloakService) {}

  /** Always read the latest parsed token (handles refresh correctly) */
  private get token(): any {
    return this.keycloak.getKeycloakInstance().tokenParsed ?? {};
  }

  get sub(): string {
    const v = this.token.sub;
    if (!v) throw new Error('Missing Keycloak subject (sub). User not authenticated?');
    return v;
  }

  get username(): string | undefined {
    return this.token.preferred_username;
  }

  get firstName(): string | undefined {
    return this.token.given_name;
  }

  get lastName(): string | undefined {
    return this.token.family_name;
  }

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

  /**
   * Registration-time choice (user attribute mapped into the token).
   * Requires your Keycloak "User Attribute" mapper:
   * - User Attribute: selected_role
   * - Token Claim Name: selected_role
   * - Added to access token (and/or id token)
   */
  get selectedRole(): AppRole | null {
    const v = this.token?.selected_role;
    return v === 'PATIENT' || v === 'DOCTOR' ? v : null;
  }

  /**
   * Effective app role:
   * - Prefer realm roles (authorization source of truth)
   * - Fallback to selected_role (useful during onboarding before realm role is assigned)
   */
  get userRole(): AppRole | null {
    if (this.hasRole('DOCTOR')) return 'DOCTOR';
    if (this.hasRole('PATIENT')) return 'PATIENT';
    return this.selectedRole;
  }

  /** Force-refresh tokens now (so realm roles assigned by backend appear in the JWT) */
  async refreshToken(): Promise<void> {
    const kc = this.keycloak.getKeycloakInstance();

    // Force refresh even if token is still valid
    await kc.updateToken(-1);
  }

  logout() {
    return this.keycloak.logout('https://localhost/');
  }
}
