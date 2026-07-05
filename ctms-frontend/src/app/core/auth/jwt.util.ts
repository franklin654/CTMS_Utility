export interface DecodedAccessToken {
  sub: string;
  uid: number;
  roles: string[];
  exp: number;
  iat: number;
}

/** Client-side decode only, for reading claims to drive the UI (nav, guards). The server is the
 * only party that ever verifies the signature -- never trust this for an authorization decision
 * that isn't re-checked server-side. */
export function decodeAccessToken(token: string): DecodedAccessToken | null {
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }
  try {
    const payload = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = payload + '='.repeat((4 - (payload.length % 4)) % 4);
    const json = decodeURIComponent(
      atob(padded)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as DecodedAccessToken;
  } catch {
    return null;
  }
}

export function isTokenExpired(token: DecodedAccessToken): boolean {
  return token.exp * 1000 <= Date.now();
}
