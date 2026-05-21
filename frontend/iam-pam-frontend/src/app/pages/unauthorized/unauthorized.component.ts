import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-unauthorized',
  template: `
    <div class="unauth-wrap">
      <div class="unauth-card">
        <div class="unauth-icon" [class.icon--group]="isNoGroup">
          <mat-icon>{{ isNoGroup ? 'group_off' : 'lock' }}</mat-icon>
        </div>
        <h2>{{ isNoGroup ? 'Aucun tenant assigné' : 'Accès non autorisé' }}</h2>
        <p *ngIf="isNoGroup">
          Votre compte (<strong>{{ auth.username }}</strong>) n'est pas encore assigné
          à un tenant. Contactez votre administrateur pour être ajouté à un groupe Keycloak.
        </p>
        <p *ngIf="!isNoGroup">
          Vous n'avez pas les permissions nécessaires pour accéder à cette page.
        </p>
        <div class="unauth-hint" *ngIf="isNoGroup">
          <mat-icon>info_outline</mat-icon>
          Rôle détecté : <span class="role-chips">
            <span *ngFor="let r of auth.roles" class="role-chip">{{ r }}</span>
            <span *ngIf="auth.roles.length === 0" class="role-chip role-chip--none">aucun rôle</span>
          </span>
        </div>
        <button mat-raised-button color="primary" (click)="goHome()">
          <mat-icon>home</mat-icon> Retour à l'accueil
        </button>
      </div>
    </div>
  `,
  styles: [`
    .unauth-wrap {
      display: flex; align-items: center; justify-content: center;
      height: 100vh; background: var(--content-bg, #f0f2f5);
    }
    .unauth-card {
      background: #fff; border-radius: 16px; padding: 48px 40px;
      display: flex; flex-direction: column; align-items: center; gap: 16px;
      text-align: center; max-width: 440px; width: 100%;
      box-shadow: 0 4px 32px rgba(0,0,0,0.08);
    }
    .unauth-icon {
      width: 72px; height: 72px; border-radius: 50%;
      background: rgba(239,68,68,0.1); display: flex; align-items: center; justify-content: center;
      mat-icon { font-size: 36px; width: 36px; height: 36px; color: #ef4444; }
      &.icon--group { background: rgba(232,160,32,0.1); mat-icon { color: #e8a020; } }
    }
    h2 { margin: 0; font-size: 20px; font-weight: 700; color: #1e293b; }
    p  { margin: 0; font-size: 14px; color: #64748b; line-height: 1.6; }
    .unauth-hint {
      display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
      font-size: 12px; color: #64748b; padding: 10px 14px;
      background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px;
      mat-icon { font-size: 14px; width: 14px; height: 14px; color: #3b82f6; }
    }
    .role-chips { display: flex; flex-wrap: wrap; gap: 4px; }
    .role-chip {
      font-family: monospace; font-size: 11px; font-weight: 600;
      background: #e0f2fe; color: #0369a1; padding: 2px 8px; border-radius: 4px;
      &.role-chip--none { background: #fee2e2; color: #dc2626; }
    }
  `]
})
export class UnauthorizedComponent implements OnInit {

  isNoGroup = false;

  constructor(
    public auth: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    // Detect if redirected because of NO_TENANT_GROUP error
    this.route.queryParams.subscribe(params => {
      this.isNoGroup = params['reason'] === 'no_group';
    });
  }

  goHome(): void {
    if (this.auth.isAdmin())        this.router.navigate(['/admin/dashboard']);
    else if (this.auth.isTenantAdmin()) this.router.navigate(['/tenant-admin/dashboard']);
    else if (this.auth.isAuditor()) this.router.navigate(['/auditor/logs']);
    else this.router.navigate(['/user/my-requests']);
  }
}
