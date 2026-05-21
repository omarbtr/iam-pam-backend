import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { AccessRequestService } from '../../../core/services/access-request.service';
import { AccessRequestResponse } from '../../../core/models/access-request.model';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'app-active-sessions',
  templateUrl: './active-sessions.component.html',
  styleUrls: ['./active-sessions.component.scss']
})
export class ActiveSessionsComponent implements OnInit, OnDestroy {

  sessions: AccessRequestResponse[] = [];
  loading = true;
  terminating: number | null = null;

  private ticker: any;

  constructor(
    private requestService: AccessRequestService,
    private snack: MatSnackBar,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.load();
    this.ticker = setInterval(() => {}, 1000); // trigger change detection each second for countdown
  }

  ngOnDestroy(): void {
    clearInterval(this.ticker);
  }

  load(): void {
    this.loading = true;
    this.requestService.getActive().subscribe({
      next: data => { this.sessions = data; this.loading = false; },
      error: ()   => { this.loading = false; }
    });
  }

  terminate(session: AccessRequestResponse): void {
    if (this.terminating) return;
    this.terminating = session.id;
    this.requestService.terminate(session.id).subscribe({
      next: () => {
        this.sessions = this.sessions.filter(s => s.id !== session.id);
        this.terminating = null;
        this.snack.open('Session terminée', 'OK', { duration: 3000 });
      },
      error: () => {
        this.terminating = null;
        this.snack.open('Erreur lors de la terminaison', 'OK', { duration: 3000 });
      }
    });
  }

  isIndefinite(session: AccessRequestResponse): boolean {
    return !session.durationHours && !session.expiresAt;
  }

  // Returns remaining time as { label, pct, critical, notStarted }
  remaining(session: AccessRequestResponse): { label: string; pct: number; critical: boolean; notStarted: boolean } {
    if (this.isIndefinite(session)) {
      return { label: 'Accès permanent', pct: 100, critical: false, notStarted: false };
    }
    // Timer not started yet (no first access recorded)
    if (session.durationHours && !session.firstAccessAt) {
      return { label: 'Non démarré', pct: 100, critical: false, notStarted: true };
    }
    if (!session.expiresAt) {
      return { label: '—', pct: 100, critical: false, notStarted: false };
    }
    const now      = Date.now();
    const expires  = new Date(session.expiresAt).getTime();
    const diff     = expires - now;
    if (diff <= 0) return { label: 'Expiré', pct: 0, critical: true, notStarted: false };

    const total    = (session.durationHours ?? 1) * 3600 * 1000;
    const pct      = Math.max(0, Math.min(100, (diff / total) * 100));
    const h        = Math.floor(diff / 3600000);
    const m        = Math.floor((diff % 3600000) / 60000);
    const s        = Math.floor((diff % 60000) / 1000);
    const label    = h > 0 ? `${h}h ${m}m` : m > 0 ? `${m}m ${s}s` : `${s}s`;
    return { label, pct, critical: diff < 5 * 60 * 1000, notStarted: false };
  }

  typeIcon(type: string): string {
    const map: Record<string, string> = {
      SSH: 'terminal', RDP: 'desktop_windows', DATABASE: 'storage',
      WEB: 'language', API: 'api'
    };
    return map[type] ?? 'dns';
  }

  openSession(session: AccessRequestResponse): void {
    if (session.resourceType === 'RDP') {
      this.router.navigate(['/user/rdp', session.id], {
        queryParams: { name: session.resourceName }
      });
    } else if (session.resourceType === 'WEB') {
      this.router.navigate(['/user/web', session.id], {
        queryParams: { name: session.resourceName }
      });
    } else if (session.resourceType === 'DATABASE') {
      this.router.navigate(['/user/db', session.id], {
        queryParams: { name: session.resourceName }
      });
    } else {
      this.router.navigate(['/user/terminal', session.id], {
        queryParams: { name: session.resourceName }
      });
    }
  }

  /** @deprecated use openSession */
  openTerminal(session: AccessRequestResponse): void {
    this.openSession(session);
  }

  typeColor(type: string): string {
    const map: Record<string, string> = {
      SSH: '#3b82f6', RDP: '#8b5cf6', DATABASE: '#f59e0b',
      WEB: '#00c4a0', API: '#ef4444'
    };
    return map[type] ?? '#64748b';
  }
}
