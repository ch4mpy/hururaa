import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { UserService } from './user.service';

/**
 * Lets authenticated users through and sends the others to the home page (where they can log in).
 * Finer rules (who may see a direction's groups...) are the API's: pages show its `403`s.
 */
export const authenticated: CanActivateChildFn = () => {
  const router = inject(Router);
  const user = inject(UserService);
  return user.whenLoaded().pipe(map(() => user.isAuthenticated() || router.parseUrl('/')));
};
