import { Component, OnInit } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { filter } from 'rxjs/operators';
import { AuthService } from '../../core/services/auth.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles: string[];
}

@Component({
  selector: 'app-layout',
  templateUrl: './layout.component.html',
  styleUrls: ['./layout.component.scss']
})
export class LayoutComponent implements OnInit {

  navItems: NavItem[] = [
    // Super-admin
    { label: 'Tableau de bord',  icon: 'dashboard',      route: '/admin/dashboard',        roles: ['admin'] },
    { label: 'Tenants',          icon: 'business',        route: '/admin/tenants',           roles: ['admin'] },
    // Tenant-admin
    { label: 'Tableau de bord',  icon: 'dashboard',       route: '/tenant-admin/dashboard',  roles: ['tenant-admin'] },
    { label: 'Utilisateurs',     icon: 'group',           route: '/tenant-admin/users',      roles: ['tenant-admin'] },
    { label: 'Ressources',       icon: 'dns',             route: '/tenant-admin/resources',  roles: ['tenant-admin', 'pam-access'] },
    { label: 'Demandes',         icon: 'task_alt',        route: '/tenant-admin/requests',   roles: ['tenant-admin'] },
    { label: 'Annuaire AD',      icon: 'corporate_fare',  route: '/tenant-admin/ad-config',  roles: ['tenant-admin'] },
    { label: 'Sécurité MFA',    icon: 'security',        route: '/tenant-admin/mfa-config', roles: ['tenant-admin'] },
    // Auditor
    { label: 'Journaux d\'audit', icon: 'shield',         route: '/auditor/logs',            roles: ['auditor', 'tenant-admin'] },
    // User / PAM
    { label: 'Mes demandes',     icon: 'inbox',           route: '/user/my-requests',        roles: ['user', 'pam-access', 'tenant-admin'] },
    { label: 'Sessions actives', icon: 'verified_user',   route: '/user/sessions',           roles: ['user', 'pam-access', 'tenant-admin'] },
    // Profile
    { label: 'Sécurité MFA',    icon: 'phonelink_lock',  route: '/profile/mfa',             roles: [] },
  ];

  visibleNavItems: NavItem[] = [];
  currentPageLabel = '';

  constructor(public auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.visibleNavItems = this.navItems.filter(item =>
      item.roles.length === 0 || item.roles.some(role => this.auth.roles.includes(role))
    );

    this.updatePageLabel(this.router.url);
    this.router.events.pipe(
      filter(e => e instanceof NavigationEnd)
    ).subscribe((e: any) => this.updatePageLabel(e.url));
  }

  updatePageLabel(url: string): void {
    const match = this.navItems.find(n => url.startsWith(n.route));
    this.currentPageLabel = match?.label ?? 'Accueil';
  }

  logout(): void {
    this.auth.logout();
  }
}
