import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { TenantService } from '../../core/services/tenant.service';
import { FaceService } from '../../core/services/face.service';
import { FaceAuthService } from '../../core/services/face-auth.service';
import { MfaService } from '../../core/services/mfa.service';
import { KeycloakService } from 'keycloak-angular';
import { forkJoin } from 'rxjs';

const FACE_VERIFIED_KEY = 'iam_face_verified';
const MFA_VERIFIED_KEY  = 'iam_mfa_verified';

@Component({
  selector: 'app-home',
  template: `<div style="display:flex;align-items:center;justify-content:center;height:60vh">
    <mat-spinner diameter="48"></mat-spinner>
  </div>`
})
export class HomeComponent implements OnInit {

  constructor(
    private auth: AuthService,
    private router: Router,
    private tenantService: TenantService,
    private faceService: FaceService,
    private faceAuthSvc: FaceAuthService,
    private mfaService: MfaService,
    private keycloak: KeycloakService
  ) {}

  ngOnInit(): void {
    if (this.faceAuthSvc.isAuthenticated()) {
      this.checkMfaThenProceed();
      return;
    }

    if (!this.keycloak.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }

    const alreadyFaceVerified = sessionStorage.getItem(FACE_VERIFIED_KEY) === 'true';
    if (!alreadyFaceVerified && !this.auth.isAdmin()) {
      this.faceService.getStatus().subscribe({
        next: s => {
          if (s.enrolled) {
            const returnUrl = this.getRoleBasedHome();
            this.router.navigate(['/profile/face-verify'],
              { queryParams: { returnUrl, fromLogin: 'true' } });
          } else {
            this.checkMfaThenProceed();
          }
        },
        error: () => this.checkMfaThenProceed()
      });
    } else {
      this.checkMfaThenProceed();
    }
  }

  private checkMfaThenProceed(): void {
    const mfaVerified = sessionStorage.getItem(MFA_VERIFIED_KEY);
    if (mfaVerified) {
      this.proceedToHome();
      return;
    }

    // Admins skip MFA check (no tenant context)
    if (this.auth.isAdmin()) {
      this.proceedToHome();
      return;
    }

    this.mfaService.getStatus().subscribe({
      next: status => {
        if (status.enrolled) {
          const returnUrl = this.getRoleBasedHome();
          this.router.navigate(['/mfa-verify'], { queryParams: { returnUrl } });
        } else {
          this.mfaService.getTenantConfig().subscribe({
            next: cfg => {
              if (cfg.mfaRequired) {
                this.router.navigate(['/profile/mfa']);
              } else {
                this.proceedToHome();
              }
            },
            error: () => this.proceedToHome()
          });
        }
      },
      error: () => this.proceedToHome()
    });
  }

  private proceedToHome(): void {
    const roles = this.auth.roles;
    if (roles.includes('admin')) {
      this.router.navigate(['/admin/dashboard']);
    } else if (roles.includes('tenant-admin')) {
      this.tenantService.isDomainConfigured().subscribe({
        next: configured => this.router.navigate([configured ? '/tenant-admin/dashboard' : '/setup']),
        error: err => { if (err?.status !== 403) this.router.navigate(['/tenant-admin/dashboard']); }
      });
    } else if (roles.includes('pam-access')) {
      this.router.navigate(['/tenant-admin/resources']);
    } else if (roles.includes('auditor')) {
      this.router.navigate(['/auditor/logs']);
    } else {
      this.router.navigate(['/user/my-requests']);
    }
  }

  private getRoleBasedHome(): string {
    const roles = this.auth.roles;
    if (roles.includes('admin'))        return '/admin/dashboard';
    if (roles.includes('tenant-admin')) return '/tenant-admin/dashboard';
    if (roles.includes('pam-access'))   return '/tenant-admin/resources';
    if (roles.includes('auditor'))      return '/auditor/logs';
    return '/user/my-requests';
  }
}
