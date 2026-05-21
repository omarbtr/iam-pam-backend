import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AccessRequestService } from '../../../core/services/access-request.service';
import { ResourceService } from '../../../core/services/resource.service';
import { AccessRequestResponse, RequestStatus } from '../../../core/models/access-request.model';
import { ResourceResponse } from '../../../core/models/resource.model';

@Component({
  selector: 'app-my-requests',
  templateUrl: './my-requests.component.html',
  styleUrls: ['./my-requests.component.scss']
})
export class MyRequestsComponent implements OnInit {

  myRequests: AccessRequestResponse[] = [];
  resources: ResourceResponse[] = [];
  showForm = false;
  form!: FormGroup;
  loading = false;
  indefinite = false;

  statusFilter: RequestStatus | 'all' = 'all';

  displayedColumns = ['resource', 'justification', 'duration', 'status', 'requestedAt', 'expiresAt'];

  readonly statusFilters: { label: string; value: RequestStatus | 'all'; icon: string }[] = [
    { label: 'Toutes',    value: 'all',      icon: 'inbox' },
    { label: 'En attente', value: 'PENDING',  icon: 'hourglass_empty' },
    { label: 'Approuvées', value: 'APPROVED', icon: 'check_circle' },
    { label: 'Rejetées',   value: 'REJECTED', icon: 'cancel' },
    { label: 'Révoquées',  value: 'REVOKED',  icon: 'remove_circle' },
    { label: 'Expirées',   value: 'EXPIRED',  icon: 'schedule' },
  ];

  constructor(
    private requestService: AccessRequestService,
    private resourceService: ResourceService,
    private fb: FormBuilder,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      resourceId:    [null, Validators.required],
      justification: [''],
      durationHours: [1, [Validators.required, Validators.min(1)]]
    });
    this.loadResources();
    this.load();
  }

  loadResources(): void {
    this.resourceService.getAll().subscribe({
      next: r => this.resources = r.filter(x => x.isActive),
      error: err => {
        const msg = err?.error?.message ?? 'Impossible de charger les ressources';
        this.snack.open(msg, 'Fermer', { duration: 5000 });
      }
    });
  }

  load(): void {
    this.loading = true;
    this.requestService.getMine().subscribe({
      next: data => { this.myRequests = data; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  get filteredRequests(): AccessRequestResponse[] {
    if (this.statusFilter === 'all') return this.myRequests;
    return this.myRequests.filter(r => r.status === this.statusFilter);
  }

  countByStatus(status: RequestStatus | 'all'): number {
    if (status === 'all') return this.myRequests.length;
    return this.myRequests.filter(r => r.status === status).length;
  }

  get pendingCount(): number { return this.myRequests.filter(r => r.status === 'PENDING').length; }
  get approvedCount(): number { return this.myRequests.filter(r => r.status === 'APPROVED').length; }

  toggleIndefinite(): void {
    this.indefinite = !this.indefinite;
    const ctrl = this.form.get('durationHours')!;
    if (this.indefinite) {
      ctrl.clearValidators();
      ctrl.setValue(null);
    } else {
      ctrl.setValidators([Validators.required, Validators.min(1)]);
      ctrl.setValue(1);
    }
    ctrl.updateValueAndValidity();
  }

  submit(): void {
    if (!this.indefinite && this.form.invalid) return;
    const payload = {
      ...this.form.value,
      durationHours: this.indefinite ? null : this.form.value.durationHours
    };
    this.requestService.create(payload).subscribe({
      next: () => {
        this.snack.open('Demande envoyée', 'OK', { duration: 3000 });
        this.showForm = false;
        this.indefinite = false;
        this.form.reset({ durationHours: 1 });
        this.load();
      },
      error: () => this.snack.open('Erreur lors de la création', 'Fermer', { duration: 3000 })
    });
  }

  openForm(): void {
    this.showForm = true;
    this.indefinite = false;
    this.form.reset({ durationHours: 1 });
    this.form.get('durationHours')!.setValidators([Validators.required, Validators.min(1)]);
    this.form.get('durationHours')!.updateValueAndValidity();
  }

  statusClass(status: RequestStatus): string {
    const map: Record<RequestStatus, string> = {
      PENDING: 'status-pending', APPROVED: 'status-approved',
      REJECTED: 'status-rejected', REVOKED: 'status-revoked', EXPIRED: 'status-expired'
    };
    return map[status];
  }

  statusIcon(status: RequestStatus): string {
    const map: Record<RequestStatus, string> = {
      PENDING: 'hourglass_empty', APPROVED: 'check_circle',
      REJECTED: 'cancel', REVOKED: 'remove_circle', EXPIRED: 'schedule'
    };
    return map[status];
  }

  resourceTypeIcon(type: string): string {
    const icons: Record<string, string> = {
      SSH: 'terminal', RDP: 'desktop_windows', DATABASE: 'storage', WEB: 'language', API: 'api'
    };
    return icons[type] ?? 'dns';
  }

  isExpiringSoon(req: AccessRequestResponse): boolean {
    if (req.status !== 'APPROVED' || !req.expiresAt) return false;
    const diff = new Date(req.expiresAt).getTime() - Date.now();
    return diff > 0 && diff < 60 * 60 * 1000; // less than 1 hour
  }
}
