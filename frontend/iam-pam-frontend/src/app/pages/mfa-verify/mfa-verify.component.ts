import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MfaService } from '../../core/services/mfa.service';
import { MfaStatus } from '../../core/models/mfa.model';

const MFA_VERIFIED_KEY = 'iam_mfa_verified';

@Component({
  selector: 'app-mfa-verify',
  templateUrl: './mfa-verify.component.html',
  styleUrls: ['./mfa-verify.component.scss']
})
export class MfaVerifyComponent implements OnInit {

  status: MfaStatus | null = null;
  loading = true;
  verifying = false;
  otpSent = false;

  form!: FormGroup;
  private returnUrl = '';
  errorMsg = '';

  constructor(
    private mfaService: MfaService,
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(p => { this.returnUrl = p['returnUrl'] || ''; });
    this.form = this.fb.group({ code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]] });

    this.mfaService.getStatus().subscribe({
      next: s => {
        this.status = s;
        this.loading = false;
        if (s.enrolled && (s.method === 'EMAIL' || s.method === 'SMS' || s.method === 'WHATSAPP')) {
          this.sendOtp();
        }
      },
      error: () => { this.loading = false; this.navigateAway(); }
    });
  }

  get methodIcon(): string {
    switch (this.status?.method) {
      case 'TOTP':     return 'phonelink_lock';
      case 'EMAIL':    return 'email';
      case 'SMS':      return 'sms';
      case 'WHATSAPP': return 'whatsapp';
      default:         return 'security';
    }
  }

  get methodLabel(): string {
    switch (this.status?.method) {
      case 'TOTP':     return 'Application Authenticator (TOTP)';
      case 'EMAIL':    return 'Email OTP';
      case 'SMS':      return 'SMS OTP';
      case 'WHATSAPP': return 'WhatsApp OTP';
      default:         return 'MFA';
    }
  }

  get instruction(): string {
    switch (this.status?.method) {
      case 'TOTP':     return 'Ouvrez votre application d\'authentification et saisissez le code affiché.';
      case 'EMAIL':    return 'Saisissez le code à 6 chiffres envoyé à votre adresse email.';
      case 'SMS':      return 'Saisissez le code à 6 chiffres reçu par SMS.';
      case 'WHATSAPP': return 'Saisissez le code à 6 chiffres reçu sur WhatsApp.';
      default:         return 'Saisissez votre code de vérification.';
    }
  }

  sendOtp(): void {
    this.mfaService.sendOtp().subscribe({
      next: r => { this.otpSent = true; this.snack.open(r.message, 'OK', { duration: 4000 }); },
      error: () => {}
    });
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.verifying = true;
    this.errorMsg = '';

    this.mfaService.verify(this.form.value.code).subscribe({
      next: res => {
        this.verifying = false;
        if (res.success) {
          sessionStorage.setItem(MFA_VERIFIED_KEY, 'true');
          this.navigateAway();
        } else {
          this.errorMsg = res.message;
          this.form.reset();
        }
      },
      error: err => {
        this.verifying = false;
        this.errorMsg = err?.error?.message ?? 'Erreur de vérification. Réessayez.';
        this.form.reset();
      }
    });
  }

  skip(): void {
    sessionStorage.setItem(MFA_VERIFIED_KEY, 'skipped');
    this.navigateAway();
  }

  private navigateAway(): void {
    if (this.returnUrl) this.router.navigateByUrl(this.returnUrl);
    else this.router.navigate(['/']);
  }
}
