import { Component, OnInit } from '@angular/core';
import { AuditService } from '../../../core/services/audit.service';
import { AuditLogResponse, PageResponse } from '../../../core/models/audit.model';

@Component({
  selector: 'app-auditor-logs',
  templateUrl: './auditor-logs.component.html',
  styleUrls: ['./auditor-logs.component.scss']
})
export class AuditorLogsComponent implements OnInit {

  page: PageResponse<AuditLogResponse> | null = null;
  currentPage = 0;
  pageSize = 25;
  loading = false;
  exporting = false;

  searchUsername = '';
  filterAction = '';
  dateFrom = '';
  dateTo = '';

  displayedColumns = ['timestamp', 'username', 'action', 'resource', 'details', 'result'];

  readonly allActions = [
    'ACCESS_REQUESTED', 'ACCESS_APPROVED', 'ACCESS_REJECTED',
    'ACCESS_REVOKED', 'ACCESS_EXPIRED',
    'RESOURCE_CREATED', 'RESOURCE_UPDATED', 'RESOURCE_DELETED',
    'USER_IMPORTED', 'USER_REMOVED',
    'TENANT_CREATED', 'DOMAIN_CONFIGURED',
    'SESSION_STARTED', 'SESSION_ENDED', 'COMMAND_EXECUTED', 'PERMISSION_DENIED'
  ];

  constructor(private auditService: AuditService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.auditService.getLogs(
      this.currentPage, this.pageSize,
      this.searchUsername, this.filterAction,
      this.dateFrom, this.dateTo
    ).subscribe({
      next: data => { this.page = data; this.loading = false; },
      error: ()   => { this.loading = false; }
    });
  }

  get filteredLogs(): AuditLogResponse[] {
    return this.page?.content ?? [];
  }

  get isFiltering(): boolean {
    return !!this.searchUsername || !!this.filterAction || !!this.dateFrom || !!this.dateTo;
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.load();
  }

  clearFilters(): void {
    this.searchUsername = '';
    this.filterAction   = '';
    this.dateFrom       = '';
    this.dateTo         = '';
    this.currentPage    = 0;
    this.load();
  }

  onPageChange(event: any): void {
    this.currentPage = event.pageIndex;
    this.pageSize    = event.pageSize;
    this.load();
  }

  exportCsv(): void {
    this.exporting = true;
    this.auditService.exportLogs().subscribe({
      next: blob => {
        const url  = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href  = url;
        link.download = `audit-logs-${new Date().toISOString().slice(0,10)}.csv`;
        link.click();
        URL.revokeObjectURL(url);
        this.exporting = false;
      },
      error: () => { this.exporting = false; }
    });
  }

  // ── Style helpers ──────────────────────────────────────────

  actionClass(action: string): string {
    if (action.includes('APPROVED') || action.includes('CREATED') || action.includes('IMPORTED') || action.includes('STARTED')) return 'action--success';
    if (action.includes('REJECTED') || action.includes('DELETED') || action.includes('REMOVED') || action.includes('DENIED'))    return 'action--danger';
    if (action.includes('REVOKED')  || action.includes('EXPIRED')  || action.includes('ENDED'))  return 'action--warn';
    if (action.includes('REQUESTED')) return 'action--info';
    return 'action--default';
  }

  actionIcon(action: string): string {
    const map: Record<string, string> = {
      ACCESS_APPROVED: 'check_circle', ACCESS_REJECTED: 'cancel',
      ACCESS_REQUESTED: 'send',       ACCESS_REVOKED: 'remove_circle',
      ACCESS_EXPIRED: 'timer_off',    RESOURCE_CREATED: 'add_circle',
      RESOURCE_UPDATED: 'edit',       RESOURCE_DELETED: 'delete',
      USER_IMPORTED: 'person_add',    USER_REMOVED: 'person_remove',
      SESSION_STARTED: 'play_circle', SESSION_ENDED: 'stop_circle',
      PERMISSION_DENIED: 'block',     TENANT_CREATED: 'domain_add',
      DOMAIN_CONFIGURED: 'dns'
    };
    return map[action] ?? 'info';
  }

  // Stats summary
  countByActionGroup(): { label: string; count: number; color: string }[] {
    const logs = this.page?.content ?? [];
    return [
      { label: 'Accès', count: logs.filter(l => l.action.startsWith('ACCESS')).length, color: '#3b82f6' },
      { label: 'Ressources', count: logs.filter(l => l.action.startsWith('RESOURCE')).length, color: '#00c4a0' },
      { label: 'Utilisateurs', count: logs.filter(l => l.action.startsWith('USER')).length, color: '#8b5cf6' },
      { label: 'Sessions', count: logs.filter(l => l.action.startsWith('SESSION')).length, color: '#e8a020' },
    ].filter(g => g.count > 0);
  }
}
