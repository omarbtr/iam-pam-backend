import { Component, OnInit } from '@angular/core';
import { AuditService } from '../../../core/services/audit.service';
import { AuditLogResponse, PageResponse } from '../../../core/models/audit.model';

@Component({
  selector: 'app-audit',
  templateUrl: './audit.component.html',
  styleUrls: ['./audit.component.scss']
})
export class AuditComponent implements OnInit {

  page: PageResponse<AuditLogResponse> | null = null;
  currentPage = 0;
  pageSize = 20;
  loading = false;

  searchUsername = '';
  filterAction = '';

  displayedColumns = ['timestamp', 'username', 'action', 'resource', 'details'];

  readonly allActions = [
    'ACCESS_REQUESTED', 'ACCESS_APPROVED', 'ACCESS_REJECTED',
    'ACCESS_REVOKED', 'ACCESS_EXPIRED',
    'RESOURCE_CREATED', 'RESOURCE_UPDATED', 'RESOURCE_DELETED',
    'USER_IMPORTED', 'USER_REMOVED',
    'TENANT_CREATED', 'DOMAIN_CONFIGURED'
  ];

  constructor(private auditService: AuditService) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.auditService.getLogs(this.currentPage, this.pageSize).subscribe({
      next: data => { this.page = data; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  get filteredLogs(): AuditLogResponse[] {
    return (this.page?.content ?? []).filter(log => {
      const matchUser = !this.searchUsername ||
        log.username.toLowerCase().includes(this.searchUsername.toLowerCase());
      const matchAction = !this.filterAction || log.action === this.filterAction;
      return matchUser && matchAction;
    });
  }

  get isFiltering(): boolean {
    return !!this.searchUsername || !!this.filterAction;
  }

  clearFilters(): void {
    this.searchUsername = '';
    this.filterAction = '';
  }

  onPageChange(event: any): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }

  actionClass(action: string): string {
    if (action.includes('APPROVED') || action.includes('CREATED') || action.includes('IMPORTED')) return 'action--success';
    if (action.includes('REJECTED') || action.includes('DELETED') || action.includes('REMOVED')) return 'action--danger';
    if (action.includes('REVOKED') || action.includes('EXPIRED')) return 'action--warn';
    if (action.includes('REQUESTED')) return 'action--info';
    return 'action--default';
  }

  actionIcon(action: string): string {
    if (action.includes('APPROVED')) return 'check_circle';
    if (action.includes('REJECTED')) return 'cancel';
    if (action.includes('REQUESTED')) return 'send';
    if (action.includes('REVOKED') || action.includes('EXPIRED')) return 'remove_circle';
    if (action.includes('RESOURCE_CREATED')) return 'add_circle';
    if (action.includes('RESOURCE_UPDATED')) return 'edit';
    if (action.includes('RESOURCE_DELETED')) return 'delete';
    if (action.includes('USER_IMPORTED')) return 'person_add';
    if (action.includes('USER_REMOVED')) return 'person_remove';
    return 'info';
  }
}
