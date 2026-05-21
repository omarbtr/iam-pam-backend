import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { DirectoryUser, TenantUser, UserImportRequest } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {

  private url = `${environment.apiUrl}/admin/users`;

  constructor(private http: HttpClient) {}

  searchDirectory(query: string): Observable<DirectoryUser[]> {
    const params = new HttpParams().set('query', query);
    return this.http.get<ApiResponse<DirectoryUser[]>>(`${this.url}/directory/search`, { params }).pipe(map(r => r.data));
  }

  listDirectory(first = 0, max = 20): Observable<DirectoryUser[]> {
    const params = new HttpParams().set('first', first).set('max', max);
    return this.http.get<ApiResponse<DirectoryUser[]>>(`${this.url}/directory`, { params }).pipe(map(r => r.data));
  }

  getTenantUsers(): Observable<TenantUser[]> {
    return this.http.get<ApiResponse<TenantUser[]>>(this.url).pipe(map(r => r.data));
  }

  importUser(payload: UserImportRequest): Observable<TenantUser> {
    return this.http.post<ApiResponse<TenantUser>>(`${this.url}/import`, payload).pipe(map(r => r.data));
  }

  removeUser(username: string): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.url}/${username}`).pipe(map(() => void 0));
  }

  canAddUser(): Observable<boolean> {
    return this.http.get<ApiResponse<boolean>>(`${this.url}/can-add`).pipe(map(r => r.data));
  }
}
