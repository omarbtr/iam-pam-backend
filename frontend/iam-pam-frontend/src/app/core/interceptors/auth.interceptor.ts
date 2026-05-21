import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpRequest, HttpHandler, HttpEvent, HttpErrorResponse } from '@angular/common/http';
import { Observable, from, of, throwError } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';
import { KeycloakService } from 'keycloak-angular';
import { Router } from '@angular/router';
import { FaceAuthService } from '../services/face-auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(
    private keycloak: KeycloakService,
    private faceAuth: FaceAuthService,
    private router: Router
  ) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const tokenObs = this.keycloak.isLoggedIn()
      ? from(this.keycloak.getToken())
      : of(this.faceAuth.getToken() ?? '');

    return tokenObs.pipe(
      switchMap(token => {
        const cloned = token
          ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
          : req;
        return next.handle(cloned).pipe(
          catchError((err: HttpErrorResponse) => {
            if (err.status === 403) {
              const body = err.error as any;
              if (body?.errorCode === 'DOMAIN_NOT_CONFIGURED') {
                this.router.navigate(['/setup']);
                return throwError(() => err);
              }
              if (body?.errorCode === 'NO_TENANT_GROUP') {
                this.router.navigate(['/unauthorized'], { queryParams: { reason: 'no_group' } });
                return throwError(() => err);
              }
            }
            return throwError(() => err);
          })
        );
      })
    );
  }
}
