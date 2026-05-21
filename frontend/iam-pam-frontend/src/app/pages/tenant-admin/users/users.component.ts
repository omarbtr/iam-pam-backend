import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';
import { UserService } from '../../../core/services/user.service';
import { AdConfigService } from '../../../core/services/ad-config.service';
import { TenantUser } from '../../../core/models/user.model';
import { AdUser } from '../../../core/models/ad-config.model';

@Component({
  selector: 'app-users',
  templateUrl: './users.component.html',
  styleUrls: ['./users.component.scss']
})
export class UsersComponent implements OnInit {

  tenantUsers: TenantUser[] = [];
  adResults: AdUser[] = [];
  searchCtrl = new FormControl('');
  loadingUsers = false;
  loadingSearch = false;
  canAdd = true;
  adConfigured = false;

  displayedColumns = ['avatar', 'username', 'email', 'roles', 'actions'];
  availableRoles = ['user', 'pam-access', 'auditor'];

  constructor(
    private userService: UserService,
    private adService: AdConfigService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadTenantUsers();
    this.checkCanAdd();
    this.checkAdConfig();

    this.searchCtrl.valueChanges.pipe(
      debounceTime(400),
      distinctUntilChanged(),
      switchMap(q => {
        if (!this.adConfigured) { return of([]); }
        this.loadingSearch = true;
        return this.adService.searchUsers(q ?? '');
      })
    ).subscribe({
      next: results => { this.adResults = results; this.loadingSearch = false; },
      error: (err) => {
        this.loadingSearch = false;
        const msg = err?.error?.message ?? 'Erreur de recherche AD';
        this.snack.open(msg, 'Fermer', { duration: 4000 });
      }
    });
  }

  checkAdConfig(): void {
    this.adService.hasConfig().subscribe({
      next: has => {
        this.adConfigured = has;
        if (has) this.loadAllAdUsers();
      },
      error: () => this.adConfigured = false
    });
  }

  loadAllAdUsers(): void {
    this.loadingSearch = true;
    this.adService.searchUsers('').subscribe({
      next: results => { this.adResults = results; this.loadingSearch = false; },
      error: () => { this.loadingSearch = false; }
    });
  }

  loadTenantUsers(): void {
    this.loadingUsers = true;
    this.userService.getTenantUsers().subscribe({
      next: users => { this.tenantUsers = users; this.loadingUsers = false; },
      error: () => { this.loadingUsers = false; }
    });
  }

  checkCanAdd(): void {
    this.userService.canAddUser().subscribe({
      next: can => this.canAdd = can,
      error: () => {}
    });
  }

  isAlreadyImported(adUser: AdUser): boolean {
    return this.tenantUsers.some(u => u.username === adUser.username);
  }

  importFromAd(adUser: AdUser): void {
    if (!this.canAdd) {
      this.snack.open('Limite utilisateurs atteinte', 'Fermer', { duration: 3000 });
      return;
    }
    this.adService.importUser({
      username:  adUser.username,
      email:     adUser.email,
      firstName: adUser.firstName,
      lastName:  adUser.lastName,
      roles:     ['user']
    }).subscribe({
      next: () => {
        this.snack.open(`${adUser.username} importé depuis l'AD`, 'OK', { duration: 3000 });
        this.loadTenantUsers();
        this.checkCanAdd();
        this.adResults = [];
        this.searchCtrl.setValue('');
      },
      error: (err) => {
        const msg = err?.error?.message ?? "Erreur d'import";
        this.snack.open(msg, 'Fermer', { duration: 4000 });
      }
    });
  }

  removeUser(username: string): void {
    if (!confirm(`Retirer "${username}" du tenant ?`)) return;
    this.userService.removeUser(username).subscribe({
      next: () => { this.snack.open('Utilisateur retiré', 'OK', { duration: 3000 }); this.loadTenantUsers(); this.checkCanAdd(); },
      error: () => this.snack.open('Erreur suppression', 'Fermer', { duration: 3000 })
    });
  }

  getInitials(user: TenantUser): string {
    const first = (user.firstName || '')[0] || '';
    const last  = (user.lastName  || '')[0] || '';
    return (first + last).toUpperCase() || user.username[0].toUpperCase();
  }
}
