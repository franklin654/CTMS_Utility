import { Injectable, signal } from '@angular/core';

const ACCESS_TOKEN_KEY = 'ctms_access_token';
const REFRESH_TOKEN_KEY = 'ctms_refresh_token';

/**
 * Tokens live in localStorage, not an httpOnly cookie, since the backend issues bearer tokens over
 * a JSON API rather than setting cookies -- accepted tradeoff (XSS could read them) given no
 * separate cookie-issuing endpoint exists. Access tokens are short-lived (15 min) to bound the risk.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  readonly accessToken = signal<string | null>(localStorage.getItem(ACCESS_TOKEN_KEY));

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  setTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    this.accessToken.set(accessToken);
  }

  setAccessToken(accessToken: string): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    this.accessToken.set(accessToken);
  }

  clear(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    this.accessToken.set(null);
  }
}
