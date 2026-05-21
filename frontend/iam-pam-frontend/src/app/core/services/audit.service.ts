import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { AuditLogResponse, PageResponse } from '../models/audit.model';

@Injectable({ providedIn: 'root' })
export class AuditService {

  private url = `${environment.apiUrl}/auditor`;

  constructor(private http: HttpClient) {}

  getLogs(page = 0, size = 20, username = '', action = '', dateFrom = '', dateTo = ''): Observable<PageResponse<AuditLogResponse>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (username) params = params.set('username', username);
    if (action)   params = params.set('action', action);
    if (dateFrom) params = params.set('dateFrom', dateFrom);
    if (dateTo)   params = params.set('dateTo', dateTo);
    return this.http.get<ApiResponse<PageResponse<AuditLogResponse>>>(`${this.url}/logs`, { params }).pipe(map(r => r.data));
  }

  getUserLogs(username: string): Observable<AuditLogResponse[]> {
    return this.http.get<ApiResponse<AuditLogResponse[]>>(`${this.url}/logs/user/${username}`).pipe(map(r => r.data));
  }

  getStats(): Observable<{ activeSessions: number; sessionsByDay: { date: string; count: number }[] }> {
    return this.http.get<ApiResponse<any>>(`${this.url}/stats`).pipe(map(r => r.data));
  }

  getResourceUsage(): Observable<{ resourceName: string; resourceType: string; sessionCount: number }[]> {
    return this.http.get<ApiResponse<any>>(`${this.url}/resource-usage`).pipe(map(r => r.data));
  }

  getSessionsByDay(date: string): Observable<any[]> {
    const params = new HttpParams().set('date', date);
    return this.http.get<ApiResponse<any[]>>(`${this.url}/sessions-by-day`, { params }).pipe(map(r => r.data));
  }

  exportLogs(): Observable<Blob> {
    return this.http.get(`${this.url}/logs/export`, { responseType: 'blob' });
  }
}
