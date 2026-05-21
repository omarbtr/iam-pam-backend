import { Injectable } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';
import { FaceAuthService } from './face-auth.service';

@Injectable({ providedIn: 'root' })
export class AuthService {

  constructor(
    private keycloak: KeycloakService,
    private faceAuth: FaceAuthService
  ) {}

  get isFaceAuth(): boolean {
    return !this.keycloak.isLoggedIn() && this.faceAuth.isAuthenticated();
  }

  get username(): string {
    if (this.isFaceAuth) return this.faceAuth.getUsername();
    const token = this.keycloak.getKeycloakInstance().tokenParsed as any;
    return token?.preferred_username ?? token?.sub ?? 'Inconnu';
  }

  get roles(): string[] {
    if (this.isFaceAuth) return this.faceAuth.getRoles();
    return this.keycloak.getUserRoles(true);
  }

  isAdmin(): boolean { return this.roles.includes('admin'); }
  isTenantAdmin(): boolean { return this.roles.includes('tenant-admin'); }
  isPamAccess(): boolean { return this.roles.includes('pam-access'); }
  isAuditor(): boolean { return this.roles.includes('auditor'); }
  isUser(): boolean { return this.roles.includes('user'); }

  logout(): void {
    if (this.isFaceAuth) {
      this.faceAuth.clearToken();
      window.location.href = '/login';
      return;
    }
    this.keycloak.logout();
  }

  getTenantId(): string | null {
    if (this.isFaceAuth) return this.faceAuth.getTenantId();
    const token = this.keycloak.getKeycloakInstance().tokenParsed as any;
    const groups: string[] = token?.groups ?? [];
    return groups.length > 0 ? groups[0] : null;
  }

  get displayName(): string {
    if (this.isFaceAuth) return this.faceAuth.getUsername();
    const token = this.keycloak.getKeycloakInstance().tokenParsed as any;
    const first = token?.given_name ?? '';
    const last = token?.family_name ?? '';
    return (first + ' ' + last).trim() || this.username;
  }

  get email(): string {
    if (this.isFaceAuth) return '';
    const token = this.keycloak.getKeycloakInstance().tokenParsed as any;
    return token?.email ?? '';
  }

  async getToken(): Promise<string> {
    if (this.isFaceAuth) {
      const token = this.faceAuth.getToken();
      if (!token) throw new Error('Face auth token unavailable');
      return token;
    }
    return this.keycloak.getToken();
  }
}
