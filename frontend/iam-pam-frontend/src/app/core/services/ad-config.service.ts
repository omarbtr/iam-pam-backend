import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { AdConfigSaveRequest, AdConfigResponse, AdUser, ImportAdUserRequest } from '../models/ad-config.model';
import { TenantUser } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class AdConfigService {

  private url = `${environment.apiUrl}/admin/ad`;

  constructor(private http: HttpClient) {}

  saveConfig(payload: AdConfigSaveRequest): Observable<AdConfigResponse> {
    return this.http.post<ApiResponse<AdConfigResponse>>(`${this.url}/config`, payload).pipe(map(r => r.data));
  }

  getConfig(): Observable<AdConfigResponse> {
    return this.http.get<ApiResponse<AdConfigResponse>>(`${this.url}/config`).pipe(map(r => r.data));
  }

  hasConfig(): Observable<boolean> {
    return this.http.get<ApiResponse<boolean>>(`${this.url}/config/status`).pipe(map(r => r.data));
  }

  testConnection(): Observable<void> {
    return this.http.post<ApiResponse<void>>(`${this.url}/test`, {}).pipe(map(() => void 0));
  }

  searchUsers(query: string): Observable<AdUser[]> {
    const params = new HttpParams().set('query', query);
    return this.http.get<ApiResponse<AdUser[]>>(`${this.url}/users/search`, { params }).pipe(map(r => r.data));
  }

  importUser(payload: ImportAdUserRequest): Observable<TenantUser> {
    return this.http.post<ApiResponse<TenantUser>>(`${this.url}/users/import`, payload).pipe(map(r => r.data));
  }
}
