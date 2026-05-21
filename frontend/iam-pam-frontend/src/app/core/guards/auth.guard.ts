import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Router, UrlTree } from '@angular/router';
import { KeycloakAuthGuard, KeycloakService } from 'keycloak-angular';
import { AuthService } from '../services/auth.service';
import { FaceAuthService } from '../services/face-auth.service';

@Injectable({ providedIn: 'root' })
export class AuthGuard extends KeycloakAuthGuard {

  constructor(
    protected override router: Router,
    protected override keycloakAngular: KeycloakService,
    private auth: AuthService,
    private faceAuth: FaceAuthService
  ) {
    super(router, keycloakAngular);
  }

  async isAccessAllowed(route: ActivatedRouteSnapshot): Promise<boolean | UrlTree> {
    // Allow face-authenticated users to proceed
    if (!this.authenticated && this.faceAuth.isAuthenticated()) {
      const requiredRoles: string[] = route.data['roles'] ?? [];
      if (requiredRoles.length === 0) return true;
      const hasRole = requiredRoles.some(r => this.faceAuth.getRoles().includes(r));
      return hasRole ? true : this.router.createUrlTree([this.getHomePath()]);
    }

    if (!this.authenticated) {
      return this.router.createUrlTree(['/login']);
    }

    const requiredRoles: string[] = route.data['roles'] ?? [];
    if (requiredRoles.length === 0) return true;

    const hasRole = requiredRoles.some(role => this.roles.includes(role));
    if (!hasRole) {
      return this.router.createUrlTree([this.getHomePath()]);
    }
    return true;
  }

  private getHomePath(): string {
    const roles = this.auth.roles;
    if (roles.includes('admin'))        return '/admin/dashboard';
    if (roles.includes('tenant-admin')) return '/tenant-admin/dashboard';
    if (roles.includes('auditor'))      return '/auditor/logs';
    if (roles.includes('pam-access'))   return '/tenant-admin/resources';
    return '/user/my-requests';
  }
}
