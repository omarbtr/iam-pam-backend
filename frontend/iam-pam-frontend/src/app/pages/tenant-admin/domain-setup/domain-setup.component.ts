import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TenantService } from '../../../core/services/tenant.service';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-domain-setup',
  templateUrl: './domain-setup.component.html',
  styleUrls: ['./domain-setup.component.scss']
})
export class DomainSetupComponent implements OnInit {

  form: FormGroup;
  loading = false;
  tenantId: string | null = null;

  constructor(
    private fb: FormBuilder,
    private tenantService: TenantService,
    private auth: AuthService,
    private router: Router,
    private snack: MatSnackBar
  ) {
    this.form = this.fb.group({
      domain: ['', [Validators.required, Validators.pattern(/^[a-zA-Z0-9][a-zA-Z0-9\-\.]+\.[a-zA-Z]{2,}$/)]]
    });
  }

  ngOnInit(): void {
    this.tenantId = this.auth.getTenantId();
    // If domain already configured, go to dashboard
    this.tenantService.isDomainConfigured().subscribe({
      next: configured => {
        if (configured) this.router.navigate(['/tenant-admin/dashboard']);
      }
    });
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.tenantService.configureDomain({ domain: this.form.value.domain }).subscribe({
      next: () => {
        this.snack.open('Domaine configuré avec succès !', 'OK', { duration: 4000 });
        this.router.navigate(['/tenant-admin/dashboard']);
      },
      error: (err) => {
        this.loading = false;
        const msg = err?.error?.message ?? 'Erreur lors de la configuration du domaine';
        this.snack.open(msg, 'Fermer', { duration: 5000 });
      }
    });
  }
}
