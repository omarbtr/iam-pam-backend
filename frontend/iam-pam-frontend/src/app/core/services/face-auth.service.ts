import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

const FACE_TOKEN_KEY  = 'iam_face_token';
const FACE_PROFILE_KEY = 'iam_face_profile';

export interface FaceLoginResponse {
  token: string;
  username: string;
  roles: string[];
  tenantId: string;
  distance: number;
}

export interface FaceUserProfile {
  username: string;
  roles: string[];
  tenantId: string;
  loginTime: number;
}

@Injectable({ providedIn: 'root' })
export class FaceAuthService {

  private readonly apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  faceLogin(username: string, descriptor: Float32Array): Observable<FaceLoginResponse> {
    return this.http.post<FaceLoginResponse>(`${this.apiUrl}/public/face/login`, {
      username,
      descriptor: Array.from(descriptor)
    });
  }

  /** Silent token refresh — call when the stored token is expired but within the 30-day grace period. */
  refreshToken(): Observable<FaceLoginResponse> {
    const token = this.getToken();
    if (!token) throw new Error('No token to refresh');
    return this.http.post<FaceLoginResponse>(`${this.apiUrl}/public/face/refresh`, { token });
  }

  /** Called once after a successful face login — persists token + profile. */
  saveSession(resp: FaceLoginResponse): void {
    localStorage.setItem(FACE_TOKEN_KEY, resp.token);
    const profile: FaceUserProfile = {
      username: resp.username,
      roles:    resp.roles,
      tenantId: resp.tenantId,
      loginTime: Date.now()
    };
    localStorage.setItem(FACE_PROFILE_KEY, JSON.stringify(profile));
  }

  getToken(): string | null {
    return localStorage.getItem(FACE_TOKEN_KEY);
  }

  clearToken(): void {
    localStorage.removeItem(FACE_TOKEN_KEY);
    localStorage.removeItem(FACE_PROFILE_KEY);
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const payload = this.parseJwt(token);
      return payload.exp * 1000 > Date.now();
    } catch {
      this.clearToken();
      return false;
    }
  }

  /** Returns true if a token exists in storage but has expired (refresh candidate). */
  hasExpiredToken(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const payload = this.parseJwt(token);
      return payload.exp * 1000 <= Date.now();
    } catch { return false; }
  }

  getUsername(): string {
    return this.getProfile()?.username ?? this.getPayload()?.preferred_username ?? '';
  }

  getRoles(): string[] {
    return this.getProfile()?.roles ?? this.getPayload()?.roles ?? [];
  }

  getTenantId(): string | null {
    return this.getProfile()?.tenantId ?? this.getPayload()?.tenantId ?? null;
  }

  private getProfile(): FaceUserProfile | null {
    try {
      const raw = localStorage.getItem(FACE_PROFILE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch { return null; }
  }

  private getPayload(): any {
    const token = this.getToken();
    if (!token) return null;
    try { return this.parseJwt(token); } catch { return null; }
  }

  private parseJwt(token: string): any {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(base64));
  }
}
