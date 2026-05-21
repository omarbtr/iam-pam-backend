import { NgModule, APP_INITIALIZER } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';

import { KeycloakAngularModule, KeycloakService } from 'keycloak-angular';

import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule } from '@angular/material/dialog';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { AuthInterceptor } from './core/interceptors/auth.interceptor';
import { environment } from '../environments/environment';

import { LayoutComponent } from './shared/layout/layout.component';

// Super-admin
import { AdminDashboardComponent } from './pages/admin/dashboard/admin-dashboard.component';
import { TenantListComponent } from './pages/admin/tenant-list/tenant-list.component';
import { TenantFormComponent } from './pages/admin/tenant-form/tenant-form.component';

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
import { FaceEnrollComponent } from './pages/profile/face-enroll/face-enroll.component';
import { FaceVerifyComponent } from './pages/profile/face-verify/face-verify.component';

// MFA verify + tenant MFA config
import { MfaVerifyComponent } from './pages/mfa-verify/mfa-verify.component';
import { MfaConfigComponent } from './pages/tenant-admin/mfa-config/mfa-config.component';

// Common
import { UnauthorizedComponent } from './pages/unauthorized/unauthorized.component';
import { HomeComponent } from './pages/home/home.component';
import { LoginComponent } from './pages/login/login.component';

function initializeKeycloak(keycloak: KeycloakService) {
  return (): Promise<boolean> => {
    return keycloak.init({
      config: {
        url: environment.keycloak.url,
        realm: environment.keycloak.realm,
        clientId: environment.keycloak.clientId
      },
      initOptions: {
        onLoad: 'check-sso',
        checkLoginIframe: false,
        pkceMethod: 'S256'
      },
      enableBearerInterceptor: false,
      bearerExcludedUrls: []
    }).catch(() => false);
  };
}

@NgModule({
  declarations: [
    AppComponent,
    LayoutComponent,
    // Super-admin
    AdminDashboardComponent,
    TenantListComponent,
    TenantFormComponent,
    // Tenant-admin
    DashboardComponent,
    UsersComponent,
    ResourcesComponent,
    RequestsComponent,
    AuditComponent,
    DomainSetupComponent,
    AdConfigComponent,
    // Auditor
    AuditorLogsComponent,
    // User
    MyRequestsComponent,
    ActiveSessionsComponent,
    TerminalComponent,
    RdpViewerComponent,
    WebViewerComponent,
    DbViewerComponent,
    // Profile / Face
    ProfileComponent,
    MfaComponent,
    FaceEnrollComponent,
    FaceVerifyComponent,
    // MFA
    MfaVerifyComponent,
    MfaConfigComponent,
    // Common
    UnauthorizedComponent,
    HomeComponent,
    LoginComponent,
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    HttpClientModule,
    ReactiveFormsModule,
    FormsModule,
    KeycloakAngularModule,
    AppRoutingModule,
    MatToolbarModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatSidenavModule,
    MatListModule,
    MatCardModule,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
    MatDialogModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatSlideToggleModule
  ],
  providers: [
    {
      provide: APP_INITIALIZER,
      useFactory: initializeKeycloak,
      multi: true,
      deps: [KeycloakService]
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}
