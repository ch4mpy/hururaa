import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { DelegationsService } from './delegations.service';

/** The two areas of the app, each scoped to a direction: `/applications/:direction`, `/users/:direction`. */
export type Area = 'applications' | 'users';

/**
 * Skips the direction chooser of an area for a user who has a say on a single direction. Hurura'a
 * administrators, who have a say on all of them (and create them from the chooser), always choose.
 */
export function skipDirectionChoice(area: Area): CanActivateFn {
  return () => {
    const router = inject(Router);
    const delegations = inject(DelegationsService);
    return delegations.whenLoaded().pipe(
      map(() => {
        const directions = delegations.directions();
        return directions?.length === 1 ? router.createUrlTree(['/', area, directions[0]]) : true;
      }),
    );
  };
}

/**
 * Opens the application of a manager who manages a single one in the direction (and does not
 * administer it): there is nothing else for them to pick in the direction.
 */
export const skipApplicationChoice: CanActivateFn = (route) => {
  const router = inject(Router);
  const delegations = inject(DelegationsService);
  const direction = param(route, 'direction');
  return delegations.whenLoaded().pipe(
    map(() => {
      if (!direction || delegations.canEditApplicationsOf(direction)) {
        return true;
      }
      const managed = delegations.managedApplicationsIn(direction);
      return managed.length === 1
        ? router.createUrlTree(['/applications', direction, managed[0].id])
        : true;
    }),
  );
};

/** A route parameter, of the route or of one of its ancestors. */
function param(route: ActivatedRouteSnapshot, name: string): string | undefined {
  const values = route.pathFromRoot.map((r) => r.paramMap.get(name)).filter((value) => !!value);
  return values.at(-1) ?? undefined;
}
