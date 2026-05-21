import { Component, OnInit } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MfaService } from '../../../core/services/mfa.service';
import { TenantMfaConfig } from '../../../core/models/mfa.model';

interface MethodToggle {
  key: keyof TenantMfaConfig;
  label: string;
  desc: string;
  icon: string;
}

@Component({
  selector: 'app-mfa-config',
  templateUrl: './mfa-config.component.html',
  styleUrls: ['./mfa-config.component.scss']
})
export class MfaConfigComponent implements OnInit {

  config: TenantMfaConfig | null = null;
  loading = true;
  saving = false;

  readonly methods: MethodToggle[] = [
    { key: 'totpEnabled',     label: 'Application Authenticator (TOTP)', desc: 'Google Authenticator, Authy, Microsoft Authenticator…', icon: 'phonelink_lock' },
    { key: 'emailOtpEnabled', label: 'Email OTP',  desc: 'Code envoyé à l\'adresse email de l\'utilisateur',     icon: 'email' },
    { key: 'smsOtpEnabled',   label: 'SMS OTP',    desc: 'Code envoyé par SMS au numéro configuré',              icon: 'sms' },
    { key: 'whatsappOtpEnabled', label: 'WhatsApp OTP', desc: 'Code à usage unique envoyé sur WhatsApp (Twilio Verify)', icon: 'whatsapp' },
  ];

  constructor(private mfaService: MfaService, private snack: MatSnackBar) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.mfaService.getTenantConfig().subscribe({
      next: c => { this.config = { ...c }; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  toggle(key: keyof TenantMfaConfig): void {
    if (!this.config) return;
    (this.config as any)[key] = !(this.config as any)[key];
  }

  get enabledCount(): number {
    if (!this.config) return 0;
    return this.methods.filter(m => !!(this.config as any)[m.key]).length;
  }

  save(): void {
    if (!this.config) return;
    if (this.enabledCount === 0 && this.config.mfaRequired) {
      this.snack.open('Activez au moins une méthode MFA avant d\'imposer le MFA.', 'Fermer', { duration: 4000 });
      return;
    }
    this.saving = true;
    this.mfaService.updateTenantConfig(this.config).subscribe({
      next: c => {
        this.config = { ...c };
        this.saving = false;
        this.snack.open('Configuration MFA sauvegardée', 'OK', { duration: 3000 });
      },
      error: () => { this.saving = false; this.snack.open('Erreur lors de la sauvegarde', 'Fermer', { duration: 3000 }); }
    });
  }
}
