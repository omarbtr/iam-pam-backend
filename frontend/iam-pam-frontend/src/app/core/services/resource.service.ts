import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { ResourceRequest, ResourceResponse } from '../models/resource.model';

@Injectable({ providedIn: 'root' })
export class ResourceService {

  private url = `${environment.apiUrl}/pam/resources`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<ResourceResponse[]> {
    return this.http.get<ApiResponse<ResourceResponse[]>>(this.url).pipe(map(r => r.data));
  }

  getById(id: number): Observable<ResourceResponse> {
    return this.http.get<ApiResponse<ResourceResponse>>(`${this.url}/${id}`).pipe(map(r => r.data));
  }

  create(payload: ResourceRequest): Observable<ResourceResponse> {
    return this.http.post<ApiResponse<ResourceResponse>>(this.url, payload).pipe(map(r => r.data));
  }

  update(id: number, payload: ResourceRequest): Observable<ResourceResponse> {
    return this.http.put<ApiResponse<ResourceResponse>>(`${this.url}/${id}`, payload).pipe(map(r => r.data));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.url}/${id}`).pipe(map(() => void 0));
  }
}
