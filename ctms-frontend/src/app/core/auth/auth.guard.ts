import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export function roleGuard(allowedRoles: string[]): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (authService.hasAnyRole(allowedRoles)) {
      return true;
    }
    // Patients have no access to the staff dashboard -- send them to their own landing page
    // instead of the generic fallback (Phase 8 flagged this gap; closed here in Phase 11).
    const fallback = authService.hasAnyRole(['PATIENT_SUBJECT']) ? '/patient' : '/dashboard';
    return router.createUrlTree([fallback]);
  };
}
