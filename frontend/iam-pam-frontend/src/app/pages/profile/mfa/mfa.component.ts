import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AuthService } from '../../../core/services/auth.service';
import { FaceService, FaceStatusResponse } from '../../../core/services/face.service';
import { MfaService } from '../../../core/services/mfa.service';
import { TenantMfaConfig, MfaStatus, MfaMethod, InitEnrollResponse } from '../../../core/models/mfa.model';

type EnrollStep = 'pick' | 'setup' | 'confirm' | 'done';

@Component({
  selector: 'app-mfa',
  templateUrl: './mfa.component.html',
  styleUrls: ['./mfa.component.scss']
})
export class MfaComponent implements OnInit {

  // ── Tenant config (which methods are allowed) ─────────────────────────────
  tenantConfig: TenantMfaConfig | null = null;
  configLoading = true;

  // ── User MFA status ───────────────────────────────────────────────────────
  mfaStatus: MfaStatus | null = null;
  statusLoading = true;

  // ── Face recognition ──────────────────────────────────────────────────────
  faceStatus: FaceStatusResponse | null = null;
  faceLoading = true;
  showEnrollPanel = false;

  // ── Enrollment flow ───────────────────────────────────────────────────────
  enrollStep: EnrollStep = 'pick';
  selectedMethod: MfaMethod | null = null;
  initResp: InitEnrollResponse | null = null;
  enrollForm!: FormGroup;
  enrollEmailForm!: FormGroup;
  enrollSmsForm!: FormGroup;
  enrollWhatsappForm!: FormGroup;
  confirmForm!: FormGroup;
  enrollLoading = false;

  readonly methodDefs: {
    id: MfaMethod; label: string; desc: string; icon: string; enabledKey: keyof TenantMfaConfig
  }[] = [
    { id: 'TOTP',    label: 'Application Authenticator (TOTP)', desc: 'Google Authenticator, Authy, Microsoft Authenticator…', icon: 'phonelink_lock', enabledKey: 'totpEnabled' },
    { id: 'EMAIL',   label: 'Email OTP',   desc: 'Code à usage unique envoyé à votre adresse email', icon: 'email',   enabledKey: 'emailOtpEnabled' },
    { id: 'SMS',     label: 'SMS OTP',     desc: 'Code à usage unique envoyé par SMS',               icon: 'sms',     enabledKey: 'smsOtpEnabled' },
    { id: 'WHATSAPP', label: 'WhatsApp OTP', desc: 'Code à usage unique envoyé sur WhatsApp',          icon: 'whatsapp', enabledKey: 'whatsappOtpEnabled' },
  ];

  constructor(
    public auth: AuthService,
    private faceService: FaceService,
    private mfaService: MfaService,
    private fb: FormBuilder,
    private router: Router,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.enrollEmailForm    = this.fb.group({ email: ['', [Validators.required, Validators.email]] });
    this.enrollSmsForm     = this.fb.group({ phone: ['', Validators.required] });
    this.enrollWhatsappForm = this.fb.group({ phone: ['', Validators.required] });
    this.confirmForm       = this.fb.group({ code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]] });
    this.load();
  }

  load(): void {
    this.loadTenantConfig();
    this.loadMfaStatus();
    this.loadFaceStatus();
  }

  loadTenantConfig(): void {
    this.configLoading = true;
    this.mfaService.getTenantConfig().subscribe({
      next: c => { this.tenantConfig = c; this.configLoading = false; },
      error: () => { this.configLoading = false; }
    });
  }

  loadMfaStatus(): void {
    this.statusLoading = true;
    this.mfaService.getStatus().subscribe({
      next: s => { this.mfaStatus = s; this.statusLoading = false; },
      error: () => { this.statusLoading = false; }
    });
  }

  loadFaceStatus(): void {
    this.faceLoading = true;
    this.faceService.getStatus().subscribe({
      next: s => { this.faceStatus = s; this.faceLoading = false; },
      error: () => { this.faceLoading = false; }
    });
  }

  // ── Computed helpers ──────────────────────────────────────────────────────

  isMethodEnabled(m: typeof this.methodDefs[0]): boolean {
    return this.tenantConfig ? !!this.tenantConfig[m.enabledKey] : false;
  }

  get enabledMethods() {
    return this.methodDefs.filter(m => this.isMethodEnabled(m));
  }

  get hasNoMethodsEnabled(): boolean {
    return this.enabledMethods.length === 0;
  }

  // ── Enrollment flow ───────────────────────────────────────────────────────

  startEnroll(method: MfaMethod): void {
    this.selectedMethod = method;
    this.enrollStep = 'setup';
    this.enrollLoading = false;
    this.initResp = null;
    this.confirmForm.reset();
    this.enrollEmailForm.reset();
    this.enrollSmsForm.reset();
    this.enrollWhatsappForm.reset();
  }

  cancelEnroll(): void {
    this.enrollStep = 'pick';
    this.selectedMethod = null;
    this.initResp = null;
  }

  submitSetup(): void {
    if (this.selectedMethod === 'EMAIL' && this.enrollEmailForm.invalid) {
      this.enrollEmailForm.markAllAsTouched(); return;
    }
    if (this.selectedMethod === 'SMS' && this.enrollSmsForm.invalid) {
      this.enrollSmsForm.markAllAsTouched(); return;
    }
    if (this.selectedMethod === 'WHATSAPP' && this.enrollWhatsappForm.invalid) {
      this.enrollWhatsappForm.markAllAsTouched(); return;
    }

    this.enrollLoading = true;
    const email = this.enrollEmailForm.value.email;
    const phone = this.selectedMethod === 'WHATSAPP'
      ? this.enrollWhatsappForm.value.phone
      : this.enrollSmsForm.value.phone;

    this.mfaService.initEnroll(this.selectedMethod!, email, phone).subscribe({
      next: resp => {
        this.initResp = resp;
        this.enrollStep = 'confirm';
        this.enrollLoading = false;
      },
      error: err => {
        this.enrollLoading = false;
        this.snack.open(err?.error?.message ?? 'Erreur lors de l\'initialisation', 'Fermer', { duration: 4000 });
      }
    });
  }

  submitConfirm(): void {
    if (this.confirmForm.invalid) { this.confirmForm.markAllAsTouched(); return; }
    this.enrollLoading = true;
    this.mfaService.confirmEnroll(this.confirmForm.value.code).subscribe({
      next: () => {
        this.enrollStep = 'done';
        this.enrollLoading = false;
        this.loadMfaStatus();
        this.snack.open('MFA activé avec succès !', 'OK', { duration: 3000 });
      },
      error: err => {
        this.enrollLoading = false;
        this.snack.open(err?.error?.message ?? 'Code invalide', 'Fermer', { duration: 4000 });
      }
    });
  }

  removeMfa(): void {
    if (!confirm('Supprimer la configuration MFA ?')) return;
    this.mfaService.removeEnrollment().subscribe({
      next: () => { this.loadMfaStatus(); this.snack.open('MFA supprimé', 'OK', { duration: 3000 }); },
      error: () => this.snack.open('Erreur lors de la suppression', 'Fermer', { duration: 3000 })
    });
  }

  // ── Face recognition ──────────────────────────────────────────────────────

  toggleEnrollPanel(): void { this.showEnrollPanel = !this.showEnrollPanel; }

  onFaceEnrolled(): void {
    this.showEnrollPanel = false;
    this.loadFaceStatus();
    this.snack.open('Reconnaissance faciale activée !', 'OK', { duration: 3000 });
  }

  removeFace(): void {
    if (!confirm('Supprimer l\'enregistrement facial ?')) return;
    this.faceService.removeEnrollment().subscribe({
      next: () => { this.loadFaceStatus(); this.snack.open('Enregistrement supprimé', 'OK', { duration: 3000 }); },
      error: () => this.snack.open('Erreur lors de la suppression', 'Fermer', { duration: 3000 })
    });
  }

  goToFaceVerify(): void { this.router.navigate(['/profile/face-verify']); }

  methodIcon(m: MfaMethod): string {
    return this.methodDefs.find(d => d.id === m)?.icon ?? 'security';
  }

  methodLabel(m: MfaMethod): string {
    return this.methodDefs.find(d => d.id === m)?.label ?? m;
  }

  encodeURI(s: string): string { return encodeURIComponent(s); }
}
