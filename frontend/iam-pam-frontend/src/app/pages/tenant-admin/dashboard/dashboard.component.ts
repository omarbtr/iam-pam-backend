import { Component, OnInit } from '@angular/core';
import { trigger, style, animate, transition } from '@angular/animations';
import { Router } from '@angular/router';
import { TenantService } from '../../../core/services/tenant.service';
import { AccessRequestService } from '../../../core/services/access-request.service';
import { ResourceService } from '../../../core/services/resource.service';
import { AuditService } from '../../../core/services/audit.service';
import { TenantResponse } from '../../../core/models/tenant.model';
import { AccessRequestResponse } from '../../../core/models/access-request.model';
import { ResourceResponse } from '../../../core/models/resource.model';

type CardKey = 'users' | 'resources' | 'requests' | 'services';

interface ResourceUsage { resourceName: string; resourceType: string; sessionCount: number; }
interface DaySession {
  sessionId: number; requestId: number | null;
  userName: string; resourceName: string; resourceType: string;
  startTime: string; durationHours: number | null; status: string;
}
interface DonutSegment {
  name: string; type: string; count: number; color: string;
  dasharray: string; dashoffset: number;
}

const TYPE_COLORS: Record<string, string> = {
  SSH: '#3b82f6', RDP: '#8b5cf6', DATABASE: '#f59e0b', WEB: '#00c4a0', API: '#ef4444'
};
const TYPE_ICONS: Record<string, string> = {
  SSH: 'terminal', RDP: 'desktop_windows', DATABASE: 'storage', WEB: 'language', API: 'api'
};
const CIRCUMFERENCE = 2 * Math.PI * 60; // r=60 → ~376.99

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss'],
  animations: [
    trigger('overlay', [
      transition(':enter', [
        style({ opacity: 0, transform: 'scale(0.96) translateY(8px)' }),
        animate('180ms ease-out', style({ opacity: 1, transform: 'scale(1) translateY(0)' }))
      ]),
      transition(':leave', [
        animate('140ms ease-in', style({ opacity: 0, transform: 'scale(0.96) translateY(4px)' }))
      ])
    ]),
    trigger('slideDown', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(-10px)' }),
        animate('200ms ease-out', style({ opacity: 1, transform: 'translateY(0)' }))
      ]),
      transition(':leave', [
        animate('150ms ease-in', style({ opacity: 0, transform: 'translateY(-6px)' }))
      ])
    ])
  ]
})
export class DashboardComponent implements OnInit {

  tenant: TenantResponse | null = null;
  retentionDays: 7 | 14 | 30 = 30;
  savingRetention = false;
  retentionSaved = false;
  pendingCount = 0;
  resourceCount = 0;
  userCount = 0;
  activeSessions = 0;
  recentRequests: AccessRequestResponse[] = [];
  resources: ResourceResponse[] = [];
  loading = true;

  // Bar chart
  sessionsByDay: { date: string; count: number }[] = [];
  selectedDay: string | null = null;
  daySessions: DaySession[] = [];
  daySessionsLoading = false;

  // Donut chart
  resourceUsage: ResourceUsage[] = [];
  selectedResource: string | null = null;

  activeCard: CardKey | null = null;

  constructor(
    private tenantService: TenantService,
    private requestService: AccessRequestService,
    private resourceService: ResourceService,
    private auditService: AuditService,
    public router: Router
  ) {}

  ngOnInit(): void {
    this.tenantService.getMyTenant().subscribe(t => {
      this.tenant = t;
      this.userCount = t.currentUserCount;
      this.retentionDays = (t.auditLogRetentionDays as 7 | 14 | 30) ?? 30;
      this.loading = false;
    });
    this.requestService.getPending().subscribe(r => {
      this.pendingCount = r.length;
      this.recentRequests = r.slice(0, 6);
    });
    this.resourceService.getAll().subscribe(r => {
      this.resources = r;
      this.resourceCount = r.length;
    });
    this.auditService.getStats().subscribe(s => {
      this.activeSessions = s.activeSessions;
      this.sessionsByDay  = s.sessionsByDay;
    });
    this.auditService.getResourceUsage().subscribe(u => {
      this.resourceUsage = u;
    });
  }

  // ── KPI card overlays ─────────────────────────────────────────

  openCard(card: CardKey): void { this.activeCard = this.activeCard === card ? null : card; }
  closeCard(): void { this.activeCard = null; }
  navigateTo(path: string): void { this.closeCard(); this.router.navigate([path]); }

  userPercent(): number {
    if (!this.tenant?.maxUsers) return 0;
    return Math.min(100, Math.round((this.userCount / this.tenant.maxUsers) * 100));
  }

  resourcePercent(): number {
    if (!this.tenant?.maxResources) return 0;
    return Math.min(100, Math.round((this.activeResources() / this.tenant.maxResources) * 100));
  }

  resourcesByType(): { type: string; count: number; icon: string; color: string }[] {
    const map: Record<string, number> = {};
    this.resources.forEach(r => map[r.type] = (map[r.type] ?? 0) + 1);
    return Object.entries(map).map(([type, count]) => ({
      type, count, icon: TYPE_ICONS[type] ?? 'dns', color: TYPE_COLORS[type] ?? '#6b7280'
    }));
  }

  activeResources(): number { return this.resources.filter(r => r.isActive).length; }

  statusClass(status: string): string {
    const map: Record<string, string> = {
      PENDING: 'status-pending', APPROVED: 'status-approved',
      REJECTED: 'status-rejected', REVOKED: 'status-revoked', EXPIRED: 'status-expired'
    };
    return map[status] ?? '';
  }

  serviceIcon(s: string): string {
    const icons: Record<string, string> = {
      PAM: 'lock', IAM: 'manage_accounts', SSO: 'vpn_key', MFA: 'security', AUDIT: 'fact_check'
    };
    return icons[s] ?? 'apps';
  }

  // ── Bar chart (sessions / day) ────────────────────────────────

  chartBars(): { date: string; dayLabel: string; count: number; percent: number }[] {
    const max = Math.max(...this.sessionsByDay.map(d => d.count), 1);
    return this.sessionsByDay.map(d => {
      const dt = new Date(d.date + 'T12:00:00');
      return {
        date: d.date,
        dayLabel: dt.toLocaleDateString('fr-FR', { weekday: 'short', day: 'numeric' }),
        count: d.count,
        percent: Math.round((d.count / max) * 100)
      };
    });
  }

  selectDay(date: string): void {
    if (this.selectedDay === date) {
      this.selectedDay = null;
      this.daySessions = [];
      return;
    }
    this.selectedDay = date;
    this.daySessions = [];
    this.daySessionsLoading = true;
    this.auditService.getSessionsByDay(date).subscribe({
      next: s => { this.daySessions = s; this.daySessionsLoading = false; },
      error: () => { this.daySessionsLoading = false; }
    });
  }

  get filteredDaySessions(): DaySession[] {
    if (!this.selectedResource) return this.daySessions;
    return this.daySessions.filter(s => s.resourceName === this.selectedResource);
  }

  selectedDayLabel(): string {
    if (!this.selectedDay) return '';
    return new Date(this.selectedDay + 'T12:00:00')
      .toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
  }

  openSession(s: DaySession): void {
    if (!s.requestId) return;
    const routes: Record<string, string> = {
      SSH: '/user/terminal', RDP: '/user/rdp', WEB: '/user/web', DATABASE: '/user/db'
    };
    const route = routes[s.resourceType];
    if (route) this.router.navigate([route, s.requestId]);
  }

  statusLabel(s: string): string {
    return s === 'APPROVED' ? 'Active' : s === 'EXPIRED' ? 'Expirée'
         : s === 'REVOKED' ? 'Révoquée' : s;
  }

  typeIcon(type: string): string { return TYPE_ICONS[type] ?? 'dns'; }
  typeColor(type: string): string { return TYPE_COLORS[type] ?? '#6b7280'; }

  // ── Audit log retention ───────────────────────────────────────

  saveRetention(): void {
    if (this.savingRetention) return;
    this.savingRetention = true;
    this.retentionSaved = false;
    this.tenantService.updateRetention(this.retentionDays).subscribe({
      next: t => {
        this.tenant = t;
        this.retentionDays = (t.auditLogRetentionDays as 7 | 14 | 30) ?? 30;
        this.savingRetention = false;
        this.retentionSaved = true;
        setTimeout(() => this.retentionSaved = false, 3000);
      },
      error: () => { this.savingRetention = false; }
    });
  }

  // ── Donut chart (resources usage) ────────────────────────────

  get totalSessions(): number {
    return this.resourceUsage.reduce((s, r) => s + r.sessionCount, 0);
  }

  donutSegments(): DonutSegment[] {
    const total = this.totalSessions;
    if (total === 0) return [];
    let cumulative = 0;
    return this.resourceUsage.map(u => {
      const arc = (u.sessionCount / total) * CIRCUMFERENCE;
      const seg: DonutSegment = {
        name: u.resourceName,
        type: u.resourceType,
        count: u.sessionCount,
        color: TYPE_COLORS[u.resourceType] ?? '#64748b',
        dasharray: `${arc} ${CIRCUMFERENCE}`,
        dashoffset: -cumulative
      };
      cumulative += arc;
      return seg;
    });
  }

  selectResource(name: string): void {
    this.selectedResource = this.selectedResource === name ? null : name;
  }
}
