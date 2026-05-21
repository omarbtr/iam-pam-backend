import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdConfigService } from '../../../core/services/ad-config.service';
import { AdConfigResponse } from '../../../core/models/ad-config.model';

@Component({
  selector: 'app-ad-config',
  templateUrl: './ad-config.component.html',
  styleUrls: ['./ad-config.component.scss']
})
export class AdConfigComponent implements OnInit {

  form!: FormGroup;
  currentConfig: AdConfigResponse | null = null;
  loading = false;
  testing = false;
  saving = false;
  showPassword = false;
  connectionStatus: 'none' | 'ok' | 'fail' = 'none';

  constructor(
    private fb: FormBuilder,
    private adService: AdConfigService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      serverUrl:        ['', Validators.required],
      port:             [389, [Validators.required, Validators.min(1), Validators.max(65535)]],
      useSsl:           [false],
      bindDn:           ['', Validators.required],
      bindPassword:     ['', Validators.required],
      userSearchBase:   ['', Validators.required],
      userSearchFilter: ['(|(uid=*{0}*)(cn=*{0}*)(mail=*{0}*))'],
      usernameAttribute:  ['uid'],
      emailAttribute:     ['mail'],
      firstnameAttribute: ['givenName'],
      lastnameAttribute:  ['sn']
    });

    this.loadExisting();
  }

  loadExisting(): void {
    this.loading = true;
    this.adService.getConfig().subscribe({
      next: config => {
        this.currentConfig = config;
        this.form.patchValue({
          serverUrl:          config.serverUrl,
          port:               config.port,
          useSsl:             config.useSsl,
          bindDn:             config.bindDn,
          userSearchBase:     config.userSearchBase,
          userSearchFilter:   config.userSearchFilter,
          usernameAttribute:  config.usernameAttribute,
          emailAttribute:     config.emailAttribute,
          firstnameAttribute: config.firstnameAttribute,
          lastnameAttribute:  config.lastnameAttribute
        });
        // Password is never returned by API — make it optional when editing existing config
        this.form.get('bindPassword')!.clearValidators();
        this.form.get('bindPassword')!.updateValueAndValidity();
        this.loading = false;
      },
      error: () => { this.loading = false; } // Not configured yet → form empty, password required
    });
  }

  testConnection(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    // Save first then test
    this.saving = true;
    this.adService.saveConfig(this.form.value).subscribe({
      next: () => {
        this.saving = false;
        this.testing = true;
        this.connectionStatus = 'none';
        this.adService.testConnection().subscribe({
          next: () => {
            this.testing = false;
            this.connectionStatus = 'ok';
            this.snack.open('Connexion AD réussie !', 'OK', { duration: 4000 });
          },
          error: (err) => {
            this.testing = false;
            this.connectionStatus = 'fail';
            const msg = err?.error?.message ?? 'Connexion AD échouée';
            this.snack.open(msg, 'Fermer', { duration: 6000 });
          }
        });
      },
      error: (err) => {
        this.saving = false;
        const msg = err?.error?.message ?? 'Erreur de sauvegarde';
        this.snack.open(msg, 'Fermer', { duration: 5000 });
      }
    });
  }

  applyPreset(type: 'openldap' | 'ad'): void {
    if (type === 'openldap') {
      this.form.patchValue({
        port:             389,
        useSsl:           false,
        userSearchFilter: '(|(uid=*{0}*)(cn=*{0}*)(mail=*{0}*))',
        usernameAttribute:  'uid',
        emailAttribute:     'mail',
        firstnameAttribute: 'givenName',
        lastnameAttribute:  'sn'
      });
    } else {
      this.form.patchValue({
        port:             389,
        useSsl:           false,
        userSearchFilter: '(|(sAMAccountName=*{0}*)(cn=*{0}*)(mail=*{0}*)(userPrincipalName=*{0}*))',
        usernameAttribute:  'sAMAccountName',
        emailAttribute:     'mail',
        firstnameAttribute: 'givenName',
        lastnameAttribute:  'sn'
      });
    }
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving = true;
    this.adService.saveConfig(this.form.value).subscribe({
      next: config => {
        this.saving = false;
        this.currentConfig = config;
        this.connectionStatus = 'none';
        this.snack.open('Configuration sauvegardée', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.saving = false;
        const msg = err?.error?.message ?? 'Erreur de sauvegarde';
        this.snack.open(msg, 'Fermer', { duration: 5000 });
      }
    });
  }
}
