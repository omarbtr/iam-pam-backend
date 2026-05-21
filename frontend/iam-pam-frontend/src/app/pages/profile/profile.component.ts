import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { TenantService } from '../../core/services/tenant.service';
import { TenantResponse } from '../../core/models/tenant.model';

@Component({
  selector: 'app-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit {

  myTenant: TenantResponse | null = null;

  constructor(public auth: AuthService, private tenantService: TenantService) {}

  ngOnInit(): void {
    if (this.auth.isTenantAdmin()) {
      this.tenantService.getMyTenant().subscribe({
        next: t => this.myTenant = t,
        error: () => {}
      });
    }
  }

  getInitials(): string {
    const name = this.auth.displayName;
    return name.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) || '?';
  }

  getRoleBadgeClass(role: string): string {
    const map: Record<string, string> = {
      'admin': 'role-admin',
      'tenant-admin': 'role-tenant-admin',
      'pam-access': 'role-pam',
      'auditor': 'role-auditor',
      'user': 'role-user'
    };
    return map[role] ?? 'role-user';
  }
}
