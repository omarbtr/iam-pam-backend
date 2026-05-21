import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ResourceService } from '../../../core/services/resource.service';
import { TenantService } from '../../../core/services/tenant.service';
import { ResourceResponse, ResourceType } from '../../../core/models/resource.model';
import { TenantResponse } from '../../../core/models/tenant.model';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-resources',
  templateUrl: './resources.component.html',
  styleUrls: ['./resources.component.scss']
})
export class ResourcesComponent implements OnInit {

  resources: ResourceResponse[] = [];
  tenant: TenantResponse | null = null;
  loading = false;
  showForm = false;
  editingId: number | null = null;
  form!: FormGroup;

  searchQuery = '';
  filterType: ResourceType | '' = '';

  resourceTypes: ResourceType[] = ['SSH', 'RDP', 'DATABASE', 'WEB', 'API'];
  displayedColumns = ['name', 'type', 'host', 'port', 'status', 'actions'];

  constructor(
    private resourceService: ResourceService,
    private tenantService: TenantService,
    private fb: FormBuilder,
    private snack: MatSnackBar,
    public auth: AuthService
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.load();
    this.tenantService.getMyTenant().subscribe(t => this.tenant = t);
  }

  get activeResourceCount(): number { return this.resources.filter(r => r.isActive).length; }

  get resourceLimitPercent(): number {
    if (!this.tenant?.maxResources) return 0;
    return Math.min(100, Math.round((this.activeResourceCount / this.tenant.maxResources) * 100));
  }

  buildForm(): void {
    this.form = this.fb.group({
      name:               ['', Validators.required],
      type:               ['SSH', Validators.required],
      host:               ['', Validators.required],
      port:               [null],
      description:        [''],
      credentialUsername: [''],
      credentialPassword: [''],
      credentialPrivateKey: [''],
    });
  }

  get isWebType(): boolean {
    return this.form.get('type')?.value === 'WEB';
  }

  load(): void {
    this.loading = true;
    this.resourceService.getAll().subscribe({
      next: data => { this.resources = data; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  get filteredResources(): ResourceResponse[] {
    return this.resources.filter(r => {
      const q = this.searchQuery.toLowerCase();
      const matchSearch = !q ||
        r.name.toLowerCase().includes(q) ||
        r.host?.toLowerCase().includes(q) ||
        r.description?.toLowerCase().includes(q);
      const matchType = !this.filterType || r.type === this.filterType;
      return matchSearch && matchType;
    });
  }

  get isFiltering(): boolean { return !!this.searchQuery || !!this.filterType; }

  clearFilters(): void { this.searchQuery = ''; this.filterType = ''; }

  openCreate(): void {
    this.editingId = null;
    this.form.reset({ type: 'SSH' });
    this.showForm = true;
  }

  openEdit(r: ResourceResponse): void {
    this.editingId = r.id;
    this.form.patchValue({
      name: r.name, type: r.type, host: r.host, port: r.port,
      description: r.description,
      credentialUsername: r.credentialUsername ?? '',
      credentialPassword: '',
      credentialPrivateKey: '',
    });
    this.showForm = true;
  }

  submit(): void {
    if (this.form.invalid) return;
    const raw = this.form.value;
    // Don't overwrite existing credentials with empty strings on update
    const payload = this.editingId ? {
      ...raw,
      credentialPassword:   raw.credentialPassword   || undefined,
      credentialPrivateKey: raw.credentialPrivateKey || undefined
    } : raw;
    const obs = this.editingId
      ? this.resourceService.update(this.editingId, payload)
      : this.resourceService.create(raw);
    obs.subscribe({
      next: () => {
        this.snack.open(this.editingId ? 'Ressource mise à jour' : 'Ressource créée', 'OK', { duration: 3000 });
        this.showForm = false;
        this.load();
      },
      error: (err) => this.snack.open(err?.error?.message ?? 'Erreur sauvegarde', 'Fermer', { duration: 3000 })
    });
  }

  delete(r: ResourceResponse): void {
    this.resourceService.delete(r.id).subscribe({
      next: () => {
        this.snack.open(`"${r.name}" désactivée`, 'OK', { duration: 3000 });
        this.load();
      },
      error: () => this.snack.open('Erreur', 'Fermer', { duration: 3000 })
    });
  }

  typeIcon(type: ResourceType): string {
    const icons: Record<ResourceType, string> = {
      SSH: 'terminal', RDP: 'desktop_windows', DATABASE: 'storage', WEB: 'language', API: 'api'
    };
    return icons[type];
  }

  countByType(type: ResourceType | ''): number {
    if (!type) return this.resources.length;
    return this.resources.filter(r => r.type === type).length;
  }
}
