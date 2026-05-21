import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { TenantCreateRequest, TenantUpdateRequest, TenantDomainConfigRequest, TenantResponse } from '../models/tenant.model';

@Injectable({ providedIn: 'root' })
export class TenantService {

  private url = `${environment.apiUrl}/admin/tenants`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<TenantResponse[]> {
    return this.http.get<ApiResponse<TenantResponse[]>>(this.url).pipe(map(r => r.data));
  }

  getById(tenantId: string): Observable<TenantResponse> {
    return this.http.get<ApiResponse<TenantResponse>>(`${this.url}/${tenantId}`).pipe(map(r => r.data));
  }

  create(payload: TenantCreateRequest): Observable<TenantResponse> {
    return this.http.post<ApiResponse<TenantResponse>>(this.url, payload).pipe(map(r => r.data));
  }

  update(tenantId: string, payload: TenantUpdateRequest): Observable<TenantResponse> {
    return this.http.put<ApiResponse<TenantResponse>>(`${this.url}/${tenantId}`, payload).pipe(map(r => r.data));
  }

  deactivate(tenantId: string): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.url}/${tenantId}`).pipe(map(() => void 0));
  }

  activate(tenantId: string): Observable<TenantResponse> {
    return this.http.put<ApiResponse<TenantResponse>>(`${this.url}/${tenantId}/activate`, {}).pipe(map(r => r.data));
  }

  getMyTenant(): Observable<TenantResponse> {
    return this.http.get<ApiResponse<TenantResponse>>(`${this.url}/my`).pipe(map(r => r.data));
  }

  configureDomain(payload: TenantDomainConfigRequest): Observable<TenantResponse> {
    return this.http.post<ApiResponse<TenantResponse>>(`${this.url}/my/domain`, payload).pipe(map(r => r.data));
  }

  isDomainConfigured(): Observable<boolean> {
    return this.http.get<ApiResponse<boolean>>(`${this.url}/my/domain/status`).pipe(map(r => r.data));
  }

  assignAdmin(tenantId: string, username: string): Observable<any> {
    return this.http.post<ApiResponse<any>>(`${this.url}/${tenantId}/admins`, { username }).pipe(map(r => r.data));
  }

  updateRetention(retentionDays: number): Observable<TenantResponse> {
    return this.http.patch<ApiResponse<TenantResponse>>(
      `${this.url}/my/retention`, { retentionDays }
    ).pipe(map(r => r.data));
  }

  setResourceLimit(tenantId: string, maxResources: number | null): Observable<TenantResponse> {
    const params: Record<string, string> = {};
    if (maxResources !== null) params['maxResources'] = String(maxResources);
    return this.http.patch<ApiResponse<TenantResponse>>(
      `${this.url}/${tenantId}/resource-limit`, {}, { params }
    ).pipe(map(r => r.data));
  }
}
