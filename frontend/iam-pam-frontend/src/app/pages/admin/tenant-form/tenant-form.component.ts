import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TenantService } from '../../../core/services/tenant.service';
import { TenantResponse, ServiceType } from '../../../core/models/tenant.model';

@Component({
  selector: 'app-tenant-form',
  templateUrl: './tenant-form.component.html'
})
export class TenantFormComponent implements OnInit {

  form!: FormGroup;
  isEdit: boolean;
  loading = false;

  allServices: ServiceType[] = ['PAM', 'IAM', 'SSO', 'MFA', 'AUDIT'];

  constructor(
    private fb: FormBuilder,
    private tenantService: TenantService,
    private snack: MatSnackBar,
    private dialogRef: MatDialogRef<TenantFormComponent>,
    @Inject(MAT_DIALOG_DATA) public data: TenantResponse | null
  ) {
    this.isEdit = !!data;
  }

  ngOnInit(): void {
    this.form = this.fb.group({
      tenantId:     [this.data?.tenantId     ?? '', [Validators.required]],
      tenantName:   [this.data?.tenantName   ?? '', [Validators.required]],
      maxUsers:     [this.data?.maxUsers     ?? 10,  [Validators.required, Validators.min(1)]],
      maxResources: [this.data?.maxResources ?? null, [Validators.min(1)]],
      services:     [this.data?.services     ?? [],  [Validators.required]]
    });

    if (this.isEdit) {
      this.form.get('tenantId')?.disable();
    }
  }

  submit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    const val = this.form.getRawValue();

    const obs = this.isEdit
      ? this.tenantService.update(this.data!.tenantId, {
          tenantName: val.tenantName,
          maxUsers: val.maxUsers,
          maxResources: val.maxResources || null,
          services: val.services
        })
      : this.tenantService.create({ ...val, maxResources: val.maxResources || null });

    obs.subscribe({
      next: () => {
        this.snack.open(this.isEdit ? 'Tenant mis à jour' : 'Tenant créé', 'OK', { duration: 3000 });
        this.dialogRef.close(true);
      },
      error: () => {
        this.loading = false;
        this.snack.open('Erreur lors de la sauvegarde', 'Fermer', { duration: 3000 });
      }
    });
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
