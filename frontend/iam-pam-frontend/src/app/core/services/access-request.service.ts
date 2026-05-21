import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { AccessRequestCreate, AccessRequestReview, AccessRequestResponse } from '../models/access-request.model';

@Injectable({ providedIn: 'root' })
export class AccessRequestService {

  private url = `${environment.apiUrl}/pam/requests`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<AccessRequestResponse[]> {
    return this.http.get<ApiResponse<AccessRequestResponse[]>>(this.url).pipe(map(r => r.data));
  }

  getPending(): Observable<AccessRequestResponse[]> {
    return this.http.get<ApiResponse<AccessRequestResponse[]>>(`${this.url}/pending`).pipe(map(r => r.data));
  }

  getMine(): Observable<AccessRequestResponse[]> {
    return this.http.get<ApiResponse<AccessRequestResponse[]>>(`${this.url}/mine`).pipe(map(r => r.data));
  }

  create(payload: AccessRequestCreate): Observable<AccessRequestResponse> {
    return this.http.post<ApiResponse<AccessRequestResponse>>(this.url, payload).pipe(map(r => r.data));
  }

  review(id: number, payload: AccessRequestReview): Observable<AccessRequestResponse> {
    return this.http.put<ApiResponse<AccessRequestResponse>>(`${this.url}/${id}/review`, payload).pipe(map(r => r.data));
  }

  revoke(id: number): Observable<AccessRequestResponse> {
    return this.http.put<ApiResponse<AccessRequestResponse>>(`${this.url}/${id}/revoke`, {}).pipe(map(r => r.data));
  }

  getActive(): Observable<AccessRequestResponse[]> {
    return this.http.get<ApiResponse<AccessRequestResponse[]>>(`${this.url}/active`).pipe(map(r => r.data));
  }

  terminate(id: number): Observable<AccessRequestResponse> {
    return this.http.put<ApiResponse<AccessRequestResponse>>(`${this.url}/${id}/terminate`, {}).pipe(map(r => r.data));
  }
}
