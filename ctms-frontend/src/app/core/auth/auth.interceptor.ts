import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';

const AUTH_ENDPOINTS_WITHOUT_TOKEN = ['/api/auth/login', '/api/auth/refresh', '/api/auth/forgot-password', '/api/auth/reset-password'];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStorage = inject(TokenStorageService);
  const authService = inject(AuthService);
  const router = inject(Router);

  const skipToken = AUTH_ENDPOINTS_WITHOUT_TOKEN.some((path) => req.url.includes(path));
  const token = tokenStorage.accessToken();
  const authorizedReq = !skipToken && token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !skipToken && tokenStorage.getRefreshToken()) {
        return authService.refresh().pipe(
          catchError((refreshError) => {
            // Only a failure of the refresh call itself means the session is actually dead.
            tokenStorage.clear();
            router.navigate(['/login']);
            return throwError(() => refreshError);
          }),
          switchMap(() => {
            const retried = req.clone({
              setHeaders: { Authorization: `Bearer ${tokenStorage.accessToken()}` },
            });
            // If the retried request fails (e.g. a legitimate 401 from wrong-password on an
            // e-signature endpoint), let it propagate as-is -- do NOT treat it as a dead session.
            return next(retried);
          }),
        );
      }
      return throwError(() => error);
    }),
  );
};
