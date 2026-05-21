import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface SchemaTable {
  name: string;
  columns: string[];
}

interface Schema {
  name: string;
  tables: SchemaTable[];
  expanded?: boolean;
}

interface QueryResult {
  type: 'SELECT' | 'UPDATE';
  columns?: string[];
  rows?: string[][];
  rowCount?: number;
  rowsAffected?: number;
  executionMs: number;
  error?: string;
}

@Component({
  selector: 'app-db-viewer',
  templateUrl: './db-viewer.component.html',
  styleUrls: ['./db-viewer.component.scss']
})
export class DbViewerComponent implements OnInit, OnDestroy {

  requestId!: number;
  resourceName = 'Base de données';
  dbType = 'postgresql';
  dbName = '';

  loading = true;
  connected = false;
  error = '';

  sql = 'SELECT version();';
  running = false;
  result: QueryResult | null = null;
  queryError = '';

  schemas: Schema[] = [];
  schemaLoading = false;

  private sessionId: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.requestId = Number(this.route.snapshot.paramMap.get('requestId'));
    this.resourceName = this.route.snapshot.queryParamMap.get('name') ?? 'Base de données';
    this.startSession();
  }

  private startSession(): void {
    this.http.post<{ sessionId: string; dbType: string; dbName: string }>(
      `${environment.apiUrl}/pam/db/start/${this.requestId}`, {}
    ).subscribe({
      next: ({ sessionId, dbType, dbName }) => {
        this.sessionId = sessionId;
        this.dbType    = dbType;
        this.dbName    = dbName;
        this.loading   = false;
        this.connected = true;
        this.loadSchema();
      },
      error: err => {
        this.loading = false;
        this.error = err?.error?.error ?? err?.message
            ?? 'Impossible d\'ouvrir le tunnel DB.';
      }
    });
  }

  loadSchema(): void {
    if (!this.sessionId) return;
    this.schemaLoading = true;
    this.http.get<Schema[]>(`${environment.apiUrl}/pam/db/schema/${this.sessionId}`)
      .subscribe({
        next: schemas => {
          this.schemas = schemas.map(s => ({ ...s, expanded: true }));
          this.schemaLoading = false;
        },
        error: () => { this.schemaLoading = false; }
      });
  }

  runQuery(): void {
    if (!this.sessionId || this.running || !this.sql.trim()) return;
    this.running    = true;
    this.queryError = '';
    this.result     = null;

    this.http.post<QueryResult>(
      `${environment.apiUrl}/pam/db/query/${this.sessionId}`,
      { sql: this.sql }
    ).subscribe({
      next: res  => { this.result = res; this.running = false; },
      error: err => {
        this.queryError = err?.error?.error ?? err?.message ?? 'Erreur SQL inconnue';
        this.running = false;
      }
    });
  }

  insertSql(fragment: string): void {
    this.sql = fragment;
  }

  toggleSchema(schema: Schema): void {
    schema.expanded = !schema.expanded;
  }

  disconnect(): void {
    const sid = this.sessionId;
    this.sessionId = null;
    if (sid) {
      this.http.delete(`${environment.apiUrl}/pam/db/stop/${sid}`).subscribe();
    }
    this.router.navigate(['/user/sessions']);
  }

  dbTypeLabel(): string {
    const map: Record<string, string> = {
      postgresql: 'PostgreSQL', mysql: 'MySQL',
      mongodb: 'MongoDB',       oracle: 'Oracle'
    };
    return map[this.dbType] ?? this.dbType.toUpperCase();
  }

  dbTypeIcon(): string {
    return 'storage';
  }

  get displayColumns(): string[] {
    return this.result?.columns ?? [];
  }

  get displayRows(): string[][] {
    return (this.result?.rows as string[][]) ?? [];
  }

  ngOnDestroy(): void {
    if (this.sessionId) {
      this.http.delete(`${environment.apiUrl}/pam/db/stop/${this.sessionId}`).subscribe();
    }
  }
}
