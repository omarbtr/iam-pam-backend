import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TenantService } from '../../../core/services/tenant.service';
import { TenantResponse } from '../../../core/models/tenant.model';
import { trigger, style, animate, transition } from '@angular/animations';

@Component({
  selector: 'app-admin-dashboard',
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.scss'],
  animations: [
    trigger('fadeIn', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(12px)' }),
        animate('200ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))
      ])
    ])
  ]
})
export class AdminDashboardComponent implements OnInit {

  tenants: TenantResponse[] = [];
  loading = true;

  constructor(private tenantService: TenantService, private router: Router) {}

  ngOnInit(): void {
    this.tenantService.getAll().subscribe({
      next: data => { this.tenants = data; this.loading = false; },
      error: ()  => { this.loading = false; }
    });
  }

  get activeCount():   number { return this.tenants.filter(t => t.isActive).length; }
  get inactiveCount(): number { return this.tenants.filter(t => !t.isActive).length; }
  get totalUsers():    number { return this.tenants.reduce((s, t) => s + (t.currentUserCount ?? 0), 0); }
  get totalCapacity(): number { return this.tenants.reduce((s, t) => s + (t.maxUsers ?? 0), 0); }

  get capacityPercent(): number {
    return this.totalCapacity > 0 ? Math.round(this.totalUsers / this.totalCapacity * 100) : 0;
  }

  serviceSummary(): { name: string; count: number; color: string }[] {
    const map: Record<string, number> = {};
    this.tenants.forEach(t => (t.services ?? []).forEach(s => { map[s] = (map[s] ?? 0) + 1; }));
    const colors: Record<string, string> = {
      PAM: '#3b82f6', IAM: '#00c4a0', SSO: '#8b5cf6',
      MFA: '#e8a020', AUDIT: '#ef4444'
    };
    return Object.entries(map)
      .map(([name, count]) => ({ name, count, color: colors[name] ?? '#64748b' }))
      .sort((a, b) => b.count - a.count);
  }

  recentTenants(): TenantResponse[] {
    return [...this.tenants]
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 5);
  }

  go(path: string): void { this.router.navigate([path]); }
}
