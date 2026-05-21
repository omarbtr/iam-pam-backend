import {
  Component, OnInit, OnDestroy,
  ElementRef, ViewChild, AfterViewInit
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import { AuthService } from '../../../core/services/auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-terminal',
  templateUrl: './terminal.component.html',
  styleUrls: ['./terminal.component.scss']
})
export class TerminalComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('terminalContainer', { static: false }) containerRef!: ElementRef<HTMLDivElement>;

  requestId!: number;
  resourceName = 'Terminal SSH';
  connected = false;
  connecting = true;
  connectionError = '';

  private term!: Terminal;
  private fitAddon!: FitAddon;
  private ws!: WebSocket;
  private resizeObserver!: ResizeObserver;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private auth: AuthService
  ) {}

  ngOnInit(): void {
    this.requestId = Number(this.route.snapshot.paramMap.get('requestId'));
    this.resourceName = this.route.snapshot.queryParamMap.get('name') ?? 'Terminal SSH';
  }

  ngAfterViewInit(): void {
    this.initTerminal();
    this.connectWebSocket().catch(err => {
      this.connecting = false;
      this.connectionError = 'Impossible de récupérer le token.';
      console.error('WS init error', err);
    });
  }

  private initTerminal(): void {
    this.term = new Terminal({
      cursorBlink: true,
      fontSize: 14,
      fontFamily: '"JetBrains Mono", "Fira Code", "Courier New", monospace',
      theme: {
        background: '#0d1117',
        foreground: '#c9d1d9',
        cursor:     '#58a6ff',
        black:      '#484f58',
        red:        '#ff7b72',
        green:      '#3fb950',
        yellow:     '#d29922',
        blue:       '#58a6ff',
        magenta:    '#bc8cff',
        cyan:       '#39c5cf',
        white:      '#b1bac4',
      },
      rows: 30,
      cols: 120
    });

    this.fitAddon = new FitAddon();
    this.term.loadAddon(this.fitAddon);
    this.term.open(this.containerRef.nativeElement);
    this.fitAddon.fit();

    // Forward keystrokes to SSH
    this.term.onData((data: string) => {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(data);
      }
    });

    // Resize terminal on window resize
    this.resizeObserver = new ResizeObserver(() => {
      try { this.fitAddon.fit(); } catch {}
    });
    this.resizeObserver.observe(this.containerRef.nativeElement);
  }

  private async connectWebSocket(): Promise<void> {
    const token = await this.auth.getToken();

    const wsBase = environment.apiUrl
      .replace('http://', 'ws://')
      .replace('https://', 'wss://')
      .replace('/api', '');

    const url = `${wsBase}/ws/session/${this.requestId}?token=${token}`;
    this.ws = new WebSocket(url);

    this.ws.onopen = () => {
      this.connected = true;
      this.connecting = false;
      this.term.writeln('\r\n\x1b[32m[IAM-PAM] Connexion au bastion établie...\x1b[0m\r\n');
    };

    this.ws.onmessage = (event: MessageEvent) => {
      this.term.write(event.data);
    };

    this.ws.onerror = () => {
      this.connecting = false;
      this.connectionError = 'Impossible de se connecter au bastion.';
      this.term.writeln('\r\n\x1b[31m[IAM-PAM] Erreur de connexion WebSocket.\x1b[0m\r\n');
    };

    this.ws.onclose = (event: CloseEvent) => {
      this.connected = false;
      this.term.writeln(`\r\n\x1b[33m[IAM-PAM] Session terminée (${event.code}).\x1b[0m\r\n`);
    };
  }

  disconnect(): void {
    this.ws?.close();
    this.router.navigate(['/user/sessions']);
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.ws?.close();
    this.term?.dispose();
  }
}
