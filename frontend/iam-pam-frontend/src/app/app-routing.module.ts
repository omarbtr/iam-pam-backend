import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './core/guards/auth.guard';

import { LayoutComponent } from './shared/layout/layout.component';
import { HomeComponent } from './pages/home/home.component';

// Super-admin
import { AdminDashboardComponent } from './pages/admin/dashboard/admin-dashboard.component';
import { TenantListComponent } from './pages/admin/tenant-list/tenant-list.component';

// Tenant-admin
import { DashboardComponent } from './pages/tenant-admin/dashboard/dashboard.component';
import { UsersComponent } from './pages/tenant-admin/users/users.component';
import { ResourcesComponent } from './pages/tenant-admin/resources/resources.component';
import { RequestsComponent } from './pages/tenant-admin/requests/requests.component';
import { AuditComponent } from './pages/tenant-admin/audit/audit.component';
import { DomainSetupComponent } from './pages/tenant-admin/domain-setup/domain-setup.component';
import { AdConfigComponent } from './pages/tenant-admin/ad-config/ad-config.component';

// Auditor
import { AuditorLogsComponent } from './pages/auditor/logs/auditor-logs.component';

// User
import { MyRequestsComponent } from './pages/user/my-requests/my-requests.component';
import { ActiveSessionsComponent } from './pages/user/active-sessions/active-sessions.component';
import { TerminalComponent } from './pages/user/terminal/terminal.component';
import { RdpViewerComponent } from './pages/user/rdp-viewer/rdp-viewer.component';
import { WebViewerComponent } from './pages/user/web-viewer/web-viewer.component';
import { DbViewerComponent } from './pages/user/db-viewer/db-viewer.component';

// Profile / MFA / Face
import { ProfileComponent } from './pages/profile/profile.component';
import { MfaComponent } from './pages/profile/mfa/mfa.component';
import { FaceVerifyComponent } from './pages/profile/face-verify/face-verify.component';

import { UnauthorizedComponent } from './pages/unauthorized/unauthorized.component';
import { LoginComponent } from './pages/login/login.component';
import { MfaVerifyComponent } from './pages/mfa-verify/mfa-verify.component';
import { MfaConfigComponent } from './pages/tenant-admin/mfa-config/mfa-config.component';

const routes: Routes = [
  {
    path: '',
    component: LayoutComponent,
    canActivate: [AuthGuard],
    children: [
      { path: '',                           component: HomeComponent,             canActivate: [AuthGuard], pathMatch: 'full' },

      // ── Super-admin ────────────────────────────────────────────
      { path: 'admin/dashboard',            component: AdminDashboardComponent,   canActivate: [AuthGuard], data: { roles: ['admin'] } },
      { path: 'admin/tenants',              component: TenantListComponent,       canActivate: [AuthGuard], data: { roles: ['admin'] } },

      // ── Tenant-admin ───────────────────────────────────────────
      { path: 'tenant-admin/dashboard',     component: DashboardComponent,        canActivate: [AuthGuard], data: { roles: ['tenant-admin'] } },
      { path: 'tenant-admin/users',         component: UsersComponent,            canActivate: [AuthGuard], data: { roles: ['tenant-admin'] } },
      { path: 'tenant-admin/resources',     component: ResourcesComponent,        canActivate: [AuthGuard], data: { roles: ['tenant-admin', 'pam-access'] } },
      { path: 'tenant-admin/requests',      component: RequestsComponent,         canActivate: [AuthGuard], data: { roles: ['tenant-admin'] } },
      { path: 'tenant-admin/audit',         component: AuditComponent,            canActivate: [AuthGuard], data: { roles: ['tenant-admin', 'auditor'] } },
      { path: 'setup',                      component: DomainSetupComponent,      canActivate: [AuthGuard], data: { roles: ['tenant-admin'] } },
      { path: 'tenant-admin/ad-config',     component: AdConfigComponent,         canActivate: [AuthGuard], data: { roles: ['tenant-admin'] } },
      { path: 'tenant-admin/mfa-config',   component: MfaConfigComponent,        canActivate: [AuthGuard], data: { roles: ['tenant-admin'] } },

      // ── Auditor ────────────────────────────────────────────────
      { path: 'auditor/logs',               component: AuditorLogsComponent,      canActivate: [AuthGuard], data: { roles: ['auditor', 'tenant-admin'] } },

      // ── User / PAM-access ──────────────────────────────────────
      { path: 'user/my-requests',           component: MyRequestsComponent,       canActivate: [AuthGuard] },
      { path: 'user/sessions',              component: ActiveSessionsComponent,   canActivate: [AuthGuard] },
      { path: 'user/terminal/:requestId',   component: TerminalComponent,         canActivate: [AuthGuard] },
      { path: 'user/rdp/:requestId',        component: RdpViewerComponent,        canActivate: [AuthGuard] },
      { path: 'user/web/:requestId',        component: WebViewerComponent,        canActivate: [AuthGuard] },
      { path: 'user/db/:requestId',         component: DbViewerComponent,         canActivate: [AuthGuard] },

      // ── Profile / MFA / Face ──────────────────────────────────────
      { path: 'profile',                    component: ProfileComponent,          canActivate: [AuthGuard] },
      { path: 'profile/mfa',               component: MfaComponent,              canActivate: [AuthGuard] },
      { path: 'profile/face-verify',       component: FaceVerifyComponent,       canActivate: [AuthGuard] },
    ]
  },
  { path: 'mfa-verify',   component: MfaVerifyComponent },
  { path: 'login',        component: LoginComponent },
  { path: 'unauthorized', component: UnauthorizedComponent },
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
