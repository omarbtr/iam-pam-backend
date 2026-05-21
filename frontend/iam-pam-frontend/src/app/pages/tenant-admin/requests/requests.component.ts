import { Component, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AccessRequestService } from '../../../core/services/access-request.service';
import { AccessRequestResponse, RequestStatus } from '../../../core/models/access-request.model';

@Component({
  selector: 'app-requests',
  templateUrl: './requests.component.html',
  styleUrls: ['./requests.component.scss']
})
export class RequestsComponent implements OnInit {

  requests: AccessRequestResponse[] = [];
  statusFilter: string = 'all';
  searchQuery = '';
  loading = false;

  // Review inline
  reviewingId: number | null = null;
  reviewComment = '';
  reviewAction: 'APPROVED' | 'REJECTED' | null = null;

  displayedColumns = ['requester', 'resource', 'justification', 'duration', 'status', 'requestedAt', 'actions'];

  readonly statusFilters = [
    { value: 'all',      label: 'Toutes',    icon: 'list' },
    { value: 'PENDING',  label: 'En attente', icon: 'pending_actions' },
    { value: 'APPROVED', label: 'Approuvées', icon: 'check_circle' },
    { value: 'REJECTED', label: 'Rejetées',   icon: 'cancel' },
    { value: 'REVOKED',  label: 'Révoquées',  icon: 'remove_circle' },
    { value: 'EXPIRED',  label: 'Expirées',   icon: 'timer_off' },
  ];

  constructor(private requestService: AccessRequestService, private snack: MatSnackBar) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.requestService.getAll().subscribe({
      next: data => { this.requests = data; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  get filteredRequests(): AccessRequestResponse[] {
    return this.requests.filter(r => {
      const matchStatus = this.statusFilter === 'all' || r.status === this.statusFilter;
      const q = this.searchQuery.toLowerCase();
      const matchSearch = !q ||
        r.requesterUsername.toLowerCase().includes(q) ||
        r.resourceName?.toLowerCase().includes(q) ||
        r.justification?.toLowerCase().includes(q);
      return matchStatus && matchSearch;
    });
  }

  get pendingCount(): number {
    return this.requests.filter(r => r.status === 'PENDING').length;
  }

  openReview(req: AccessRequestResponse, action: 'APPROVED' | 'REJECTED'): void {
    this.reviewingId = req.id;
    this.reviewAction = action;
    this.reviewComment = '';
  }

  cancelReview(): void {
    this.reviewingId = null;
    this.reviewAction = null;
    this.reviewComment = '';
  }

  confirmReview(): void {
    if (!this.reviewingId || !this.reviewAction) return;
    this.requestService.review(this.reviewingId, { status: this.reviewAction, comment: this.reviewComment }).subscribe({
      next: () => {
        const msg = this.reviewAction === 'APPROVED' ? 'Demande approuvée ✓' : 'Demande rejetée';
        this.snack.open(msg, 'OK', { duration: 3000 });
        this.cancelReview();
        this.load();
      },
      error: () => this.snack.open('Erreur lors de la révision', 'Fermer', { duration: 3000 })
    });
  }

  revoke(req: AccessRequestResponse): void {
    this.requestService.revoke(req.id).subscribe({
      next: () => { this.snack.open('Accès révoqué', 'OK', { duration: 3000 }); this.load(); },
      error: () => this.snack.open('Erreur', 'Fermer', { duration: 3000 })
    });
  }

  statusClass(status: RequestStatus): string {
    const map: Record<RequestStatus, string> = {
      PENDING: 'status-pending', APPROVED: 'status-approved',
      REJECTED: 'status-rejected', REVOKED: 'status-revoked', EXPIRED: 'status-expired'
    };
    return map[status] ?? '';
  }

  countByStatus(status: string): number {
    if (status === 'all') return this.requests.length;
    return this.requests.filter(r => r.status === status).length;
  }
}
