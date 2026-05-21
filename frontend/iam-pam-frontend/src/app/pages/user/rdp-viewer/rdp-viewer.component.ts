import {
  Component, OnInit, OnDestroy,
  ElementRef, ViewChild, AfterViewInit,
  ChangeDetectorRef, NgZone
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { environment } from '../../../../environments/environment';

interface RdpAuditEntry {
  id: number;
  eventType: string;
  detail: string;
  occurredAt: string;
}

declare const Guacamole: any;

@Component({
  selector: 'app-rdp-viewer',
  templateUrl: './rdp-viewer.component.html',
  styleUrls: ['./rdp-viewer.component.scss']
})
export class RdpViewerComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('displayContainer', { static: false }) containerRef!: ElementRef<HTMLDivElement>;

  requestId!: number;
  resourceName = 'Bureau RDP';
  connected = false;
  connecting = true;
  connectionError = '';

  // Audit log panel
  showAuditPanel = false;
  auditLogs: RdpAuditEntry[] = [];
  private auditPoll?: ReturnType<typeof setInterval>;

  private client: any;
  private tunnel: any;
  private keyboard: any;
  private mouse: any;
  private resizeObserver!: ResizeObserver;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private auth: AuthService,
    private http: HttpClient,
    private zone: NgZone,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.requestId = Number(this.route.snapshot.paramMap.get('requestId'));
    this.resourceName = this.route.snapshot.queryParamMap.get('name') ?? 'Bureau RDP';
  }

  ngAfterViewInit(): void {
    this.connectRdp().catch(err => {
      this.connecting = false;
      this.connectionError = err?.message ?? String(err);
      console.error('RDP init error', err);
    });
  }

  private async connectRdp(): Promise<void> {
    if (typeof Guacamole === 'undefined') {
      throw new Error('Guacamole library not loaded');
    }

    const token = await this.auth.getToken();

    const wsBase = environment.apiUrl
      .replace('http://', 'ws://')
      .replace('https://', 'wss://')
      .replace('/api', '');

    // WebSocketTunnel.connect(data) appends "?" + data to the URL.
    // Pass an empty base URL and send the token via connect() so the
    // final WebSocket URL is: /ws/rdp/{id}?token=<jwt>
    const baseUrl = `${wsBase}/ws/rdp/${this.requestId}`;
    this.tunnel = new Guacamole.WebSocketTunnel(baseUrl);
    this.client = new Guacamole.Client(this.tunnel);

    // Attach canvas to DOM
    // isolation: isolate forces a new stacking context so that z-index: -1 canvases
    // inside the guac div are visible (otherwise they paint behind .rdp-wrap's #000 background)
    const display = this.client.getDisplay().getElement();
    display.style.isolation = 'isolate';
    this.containerRef.nativeElement.appendChild(display);

    // Connection state — callbacks fire outside Angular zone, must use zone.run()
    this.client.onstatechange = (state: number) => {
      console.log('[Guacamole] state:', state);
      this.zone.run(() => {
        switch (state) {
          case 3: // CONNECTED
            this.connected = true;
            this.connecting = false;
            this.sendSize();
            break;
          case 5: // DISCONNECTED
            this.connected = false;
            this.connecting = false;
            break;
        }
      });
    };

    this.client.onerror = (error: any) => {
      console.error('[Guacamole] error:', error);
      this.zone.run(() => {
        this.connected = false;
        this.connecting = false;
        this.connectionError = error?.message ?? 'Erreur de connexion RDP.';
      });
    };

    this.tunnel.onerror = (status: any) => {
      console.error('[Guacamole] tunnel error:', status);
    };

    // Mouse — 1.3.0 API: individual handlers, state passed directly
    // onmouseout can fire with an incomplete state in 1.3.0, so guard against it
    this.mouse = new Guacamole.Mouse(this.containerRef.nativeElement);
    const sendMouse = (mouseState: any) => {
      if (mouseState?.x !== undefined) this.client.sendMouseState(mouseState);
    };
    this.mouse.onmousemove = sendMouse;
    this.mouse.onmousedown = sendMouse;
    this.mouse.onmouseup   = sendMouse;
    this.mouse.onmouseout  = sendMouse;

    // Keyboard
    this.keyboard = new Guacamole.Keyboard(document);
    this.keyboard.onkeydown = (keysym: number) => this.client.sendKeyEvent(1, keysym);
    this.keyboard.onkeyup   = (keysym: number) => this.client.sendKeyEvent(0, keysym);

    // Resize observer
    this.resizeObserver = new ResizeObserver(() => this.sendSize());
    this.resizeObserver.observe(this.containerRef.nativeElement);

    // Token passed here becomes "?token=<jwt>" in the WebSocket upgrade URL
    this.client.connect(`token=${token}`);
  }

  private sendSize(): void {
    if (!this.client || !this.containerRef) return;
    const el = this.containerRef.nativeElement;
    const w = el.clientWidth  || 1280;
    const h = el.clientHeight || 720;
    this.client.sendSize(w, h);
  }

  disconnect(): void {
    this.client?.disconnect();
    this.router.navigate(['/user/sessions']);
  }

  // ── Audit log panel ──────────────────────────────────────────

  toggleAuditPanel(): void {
    this.showAuditPanel = !this.showAuditPanel;
    if (this.showAuditPanel) {
      this.loadAuditLogs();
      this.auditPoll = setInterval(() => this.loadAuditLogs(), 10000);
    } else {
      if (this.auditPoll) { clearInterval(this.auditPoll); this.auditPoll = undefined; }
    }
  }

  private loadAuditLogs(): void {
    this.http.get<any>(`${environment.apiUrl}/pam/rdp-sessions/${this.requestId}/audit-logs`)
      .subscribe({
        next: res => {
          if (res?.data) {
            this.zone.run(() => {
              this.auditLogs = [...res.data].reverse(); // newest first
              this.cdr.markForCheck();
            });
          }
        },
        error: () => {} // silent — don't interrupt session on audit failure
      });
  }

  eventIcon(type: string): string {
    const icons: Record<string, string> = {
      SESSION_START: 'play_circle', SESSION_END: 'stop_circle',
      TEXT_INPUT: 'keyboard', KEY_COMBO: 'shortcut',
      CLIPBOARD_PASTE: 'content_paste', CLIPBOARD_COPY: 'content_copy',
      FILE_TRANSFER: 'upload_file', MOUSE_CLICK: 'mouse'
    };
    return icons[type] ?? 'event_note';
  }

  eventColor(type: string): string {
    const colors: Record<string, string> = {
      SESSION_START: '#00c4a0', SESSION_END: '#ef4444',
      TEXT_INPUT: '#3b82f6', KEY_COMBO: '#f59e0b',
      CLIPBOARD_PASTE: '#8b5cf6', CLIPBOARD_COPY: '#8b5cf6',
      FILE_TRANSFER: '#f59e0b', MOUSE_CLICK: '#64748b'
    };
    return colors[type] ?? '#64748b';
  }

  ngOnDestroy(): void {
    this.keyboard?.reset();
    this.resizeObserver?.disconnect();
    this.client?.disconnect();
    if (this.auditPoll) clearInterval(this.auditPoll);
  }
}
