import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-web-viewer',
  templateUrl: './web-viewer.component.html',
  styleUrls: ['./web-viewer.component.scss']
})
export class WebViewerComponent implements OnInit, OnDestroy {

  requestId!: number;
  resourceName = 'Application Web';
  iframeSrc: SafeResourceUrl | null = null;
  loading = true;
  ready = false;
  error = '';

  private sessionId: string | null = null;
  private readonly backendBase: string;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private sanitizer: DomSanitizer,
    private http: HttpClient
  ) {
    this.backendBase = environment.apiUrl.replace('/api', '');
  }

  ngOnInit(): void {
    this.requestId = Number(this.route.snapshot.paramMap.get('requestId'));
    this.resourceName = this.route.snapshot.queryParamMap.get('name') ?? 'Application Web';
    this.startSession();
  }

  private startSession(): void {
    this.http.post<{ sessionId: string; proxyUrl: string }>(
      `${environment.apiUrl}/pam/web/start/${this.requestId}`, {}
    ).subscribe({
      next: ({ sessionId, proxyUrl }) => {
        this.sessionId = sessionId;
        this.iframeSrc = this.sanitizer.bypassSecurityTrustResourceUrl(
          `${this.backendBase}${proxyUrl}`
        );
        this.loading = false;
        this.ready = true;
      },
      error: err => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message
            ?? 'Impossible de démarrer la session Web.';
      }
    });
  }

  disconnect(): void {
    const sid = this.sessionId;
    this.sessionId = null;   // prevent double-send in ngOnDestroy
    if (sid) {
      this.http.delete(`${environment.apiUrl}/pam/web/stop/${sid}`).subscribe();
    }
    this.router.navigate(['/user/sessions']);
  }

  ngOnDestroy(): void {
    if (this.sessionId) {
      this.http.delete(`${environment.apiUrl}/pam/web/stop/${this.sessionId}`).subscribe();
    }
  }
}
