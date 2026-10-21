import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService, staffLandingUrl } from './session.service';
import { BrowserLocation } from './browser-location';

/** Only a PERSONA may open the Workspace; advisor and researcher go to their Swagger UI. */
export const workspaceGuard: CanActivateFn = async () => {
  const session = inject(SessionService);
  const router = inject(Router);
  const location = inject(BrowserLocation);
  const user = await session.load();
  if (user === null) {
    return router.createUrlTree(['/login']);
  }
  if (user.role !== 'PERSONA') {
    location.assign(staffLandingUrl(user.role));
    return false;
  }
  return true;
};
