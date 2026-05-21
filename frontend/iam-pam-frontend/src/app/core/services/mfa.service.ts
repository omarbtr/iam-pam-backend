import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  TenantMfaConfig, MfaStatus, InitEnrollResponse, VerifyResponse, MfaMethod
} from '../models/mfa.model';

@Injectable({ providedIn: 'root' })
export class MfaService {

  private readonly url = `${environment.apiUrl}/user/mfa`;

  constructor(private http: HttpClient) {}

  // ── Tenant config ────────────────────────────────────────────────────────

  getTenantConfig(): Observable<TenantMfaConfig> {
    return this.http.get<any>(`${this.url}/tenant-config`).pipe(map(r => r.data));
  }

  updateTenantConfig(cfg: Partial<TenantMfaConfig>): Observable<TenantMfaConfig> {
    return this.http.put<any>(`${this.url}/tenant-config`, cfg).pipe(map(r => r.data));
  }

  // ── Status ───────────────────────────────────────────────────────────────

  getStatus(): Observable<MfaStatus> {
    return this.http.get<any>(`${this.url}/status`).pipe(map(r => r.data));
  }

  // ── Enrollment ───────────────────────────────────────────────────────────

  initEnroll(method: MfaMethod, contactEmail?: string, phoneNumber?: string): Observable<InitEnrollResponse> {
    return this.http.post<any>(`${this.url}/enroll/init`, { method, contactEmail, phoneNumber })
      .pipe(map(r => r.data));
  }

  confirmEnroll(code: string): Observable<string> {
    return this.http.post<any>(`${this.url}/enroll/confirm`, { code }).pipe(map(r => r.data));
  }

  removeEnrollment(): Observable<string> {
    return this.http.delete<any>(`${this.url}/enroll`).pipe(map(r => r.data));
  }

  // ── Verification (post-login) ────────────────────────────────────────────

  sendOtp(): Observable<{ message: string }> {
    return this.http.post<any>(`${this.url}/send-otp`, {}).pipe(map(r => r.data));
  }

  verify(code: string): Observable<VerifyResponse> {
    return this.http.post<any>(`${this.url}/verify`, { code }).pipe(map(r => r.data));
  }
}
