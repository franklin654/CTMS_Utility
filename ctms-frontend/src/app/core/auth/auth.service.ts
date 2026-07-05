import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { TokenStorageService } from './token-storage.service';
import { decodeAccessToken, isTokenExpired } from './jwt.util';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  mustChangePassword: boolean;
}

export interface CurrentUser {
  username: string;
  userId: number;
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly currentUser = computed<CurrentUser | null>(() => {
    const token = this.tokenStorage.accessToken();
    if (!token) {
      return null;
    }
    const decoded = decodeAccessToken(token);
    if (!decoded || isTokenExpired(decoded)) {
      return null;
    }
    return { username: decoded.sub, userId: decoded.uid, roles: decoded.roles ?? [] };
  });

  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  constructor(
    private readonly http: HttpClient,
    private readonly tokenStorage: TokenStorageService,
  ) {}

  login(username: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>('/api/auth/login', { username, password })
      .pipe(tap((res) => this.tokenStorage.setTokens(res.accessToken, res.refreshToken)));
  }

  refresh(): Observable<TokenResponse> {
    const refreshToken = this.tokenStorage.getRefreshToken();
    return this.http
      .post<TokenResponse>('/api/auth/refresh', { refreshToken })
      .pipe(tap((res) => this.tokenStorage.setTokens(res.accessToken, res.refreshToken)));
  }

  logout(): Observable<void> {
    const refreshToken = this.tokenStorage.getRefreshToken();
    this.tokenStorage.clear();
    return this.http.post<void>('/api/auth/logout', { refreshToken });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/auth/change-password', { currentPassword, newPassword });
  }

  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>('/api/auth/forgot-password', { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/auth/reset-password', { token, newPassword });
  }

  hasRole(role: string): boolean {
    return this.currentUser()?.roles.includes(role) ?? false;
  }

  hasAnyRole(roles: string[]): boolean {
    const current = this.currentUser()?.roles ?? [];
    return roles.some((r) => current.includes(r));
  }
}
