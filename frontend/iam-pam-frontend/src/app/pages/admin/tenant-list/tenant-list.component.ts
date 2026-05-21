import { Component, OnInit } from '@angular/core';
import { trigger, style, animate, transition } from '@angular/animations';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TenantService } from '../../../core/services/tenant.service';
import { TenantResponse } from '../../../core/models/tenant.model';
import { TenantFormComponent } from '../tenant-form/tenant-form.component';

@Component({
  selector: 'app-tenant-list',
  templateUrl: './tenant-list.component.html',
  styleUrls: ['./tenant-list.component.scss'],
  animations: [
    trigger('drawer', [
      transition(':enter', [
        style({ transform: 'translateX(100%)', opacity: 0 }),
        animate('220ms ease-out', style({ transform: 'translateX(0)', opacity: 1 }))
      ]),
      transition(':leave', [
        animate('180ms ease-in', style({ transform: 'translateX(100%)', opacity: 0 }))
      ])
    ])
  ]
})
export class TenantListComponent implements OnInit {

  tenants: TenantResponse[] = [];
  loading = false;
  displayedColumns = ['tenantId', 'tenantName', 'admin', 'domain', 'users', 'resources', 'services', 'status', 'actions'];

  searchQuery = '';
  statusFilter: 'all' | 'active' | 'inactive' = 'all';

  // Side drawer
  selectedTenant: TenantResponse | null = null;

  // Inline assign-admin panel
  assigningTenant: TenantResponse | null = null;
  assignUsername = '';

  // Inline deactivate confirmation
  confirmingTenant: TenantResponse | null = null;

  // Inline resource-limit editor
  editingResourceLimitTenant: TenantResponse | null = null;
  resourceLimitInput: number | null = null;

  constructor(
    private tenantService: TenantService,
    private dialog: MatDialog,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.tenantService.getAll().subscribe({
      next: data => { this.tenants = data; this.loading = false; },
      error: () => { this.loading = false; this.snack.open('Erreur chargement tenants', 'Fermer', { duration: 3000 }); }
    });
  }

  get filteredTenants(): TenantResponse[] {
    const q = this.searchQuery.toLowerCase();
    return this.tenants.filter(t => {
      const matchSearch = !q ||
        t.tenantId.toLowerCase().includes(q) ||
        t.tenantName.toLowerCase().includes(q) ||
        (t.adminUsername ?? '').toLowerCase().includes(q) ||
        (t.domain ?? '').toLowerCase().includes(q);
      const matchStatus =
        this.statusFilter === 'all' ||
        (this.statusFilter === 'active' && t.isActive) ||
        (this.statusFilter === 'inactive' && !t.isActive);
      return matchSearch && matchStatus;
    });
  }

  get activeCount(): number { return this.tenants.filter(t => t.isActive).length; }
  get inactiveCount(): number { return this.tenants.filter(t => !t.isActive).length; }
  get totalUsers(): number { return this.tenants.reduce((acc, t) => acc + (t.currentUserCount ?? 0), 0); }
  get isFiltering(): boolean { return !!this.searchQuery || this.statusFilter !== 'all'; }

  clearFilters(): void { this.searchQuery = ''; this.statusFilter = 'all'; }

  selectTenant(t: TenantResponse): void {
    this.selectedTenant = this.selectedTenant?.tenantId === t.tenantId ? null : t;
  }

  closeDrawer(): void { this.selectedTenant = null; }

  userPercent(t: TenantResponse): number {
    if (!t.maxUsers) return 0;
    return Math.min(100, Math.round((t.currentUserCount / t.maxUsers) * 100));
  }

  resourcePercent(t: TenantResponse): number {
    if (!t.maxResources) return 0;
    return Math.min(100, Math.round((t.currentResources / t.maxResources) * 100));
  }

  openEditResourceLimit(tenant: TenantResponse, event?: Event): void {
    event?.stopPropagation();
    this.editingResourceLimitTenant = tenant;
    this.resourceLimitInput = tenant.maxResources;
  }

  cancelEditResourceLimit(): void {
    this.editingResourceLimitTenant = null;
    this.resourceLimitInput = null;
  }

  confirmEditResourceLimit(): void {
    if (!this.editingResourceLimitTenant) return;
    const t = this.editingResourceLimitTenant;
    this.editingResourceLimitTenant = null;
    this.tenantService.setResourceLimit(t.tenantId, this.resourceLimitInput).subscribe({
      next: () => {
        this.snack.open(`Limite ressources de "${t.tenantName}" mise à jour`, 'OK', { duration: 3000 });
        this.load();
        if (this.selectedTenant?.tenantId === t.tenantId) this.closeDrawer();
      },
      error: (err) => {
        const msg = err?.error?.message ?? 'Erreur lors de la mise à jour';
        this.snack.open(msg, 'Fermer', { duration: 5000 });
      }
    });
    this.resourceLimitInput = null;
  }

  openCreate(): void {
    const ref = this.dialog.open(TenantFormComponent, { width: '600px', data: null });
    ref.afterClosed().subscribe(result => { if (result) this.load(); });
  }

  openEdit(tenant: TenantResponse, event?: Event): void {
    event?.stopPropagation();
    const ref = this.dialog.open(TenantFormComponent, { width: '600px', data: tenant });
    ref.afterClosed().subscribe(result => { if (result) { this.load(); this.closeDrawer(); } });
  }

  openDeactivateConfirm(tenant: TenantResponse, event?: Event): void {
    event?.stopPropagation();
    this.confirmingTenant = tenant;
  }

  cancelConfirm(): void { this.confirmingTenant = null; }

  confirmDeactivate(): void {
    if (!this.confirmingTenant) return;
    const t = this.confirmingTenant;
    this.confirmingTenant = null;
    this.tenantService.deactivate(t.tenantId).subscribe({
      next: () => {
        this.snack.open(`"${t.tenantName}" désactivé`, 'OK', { duration: 3000 });
        this.load();
        if (this.selectedTenant?.tenantId === t.tenantId) this.closeDrawer();
      },
      error: () => this.snack.open('Erreur désactivation', 'Fermer', { duration: 3000 })
    });
  }

  activate(tenant: TenantResponse, event?: Event): void {
    event?.stopPropagation();
    this.tenantService.activate(tenant.tenantId).subscribe({
      next: () => { this.snack.open(`"${tenant.tenantName}" réactivé`, 'OK', { duration: 3000 }); this.load(); },
      error: () => this.snack.open('Erreur réactivation', 'Fermer', { duration: 3000 })
    });
  }

  openAssignAdmin(tenant: TenantResponse, event?: Event): void {
    event?.stopPropagation();
    this.assigningTenant = tenant;
    this.assignUsername = tenant.adminUsername ?? '';
  }

  cancelAssign(): void { this.assigningTenant = null; this.assignUsername = ''; }

  confirmAssign(): void {
    if (!this.assigningTenant || !this.assignUsername.trim()) return;
    const t = this.assigningTenant;
    const username = this.assignUsername.trim();
    this.assigningTenant = null;
    this.assignUsername = '';
    this.tenantService.assignAdmin(t.tenantId, username).subscribe({
      next: () => {
        this.snack.open(`Admin "${username}" assigné à ${t.tenantName}`, 'OK', { duration: 4000 });
        this.load();
      },
      error: (err) => {
        const msg = err?.error?.message ?? 'Utilisateur introuvable ou erreur Keycloak';
        this.snack.open(msg, 'Fermer', { duration: 5000 });
      }
    });
  }
}
