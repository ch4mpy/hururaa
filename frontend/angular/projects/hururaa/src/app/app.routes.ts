import { Routes } from '@angular/router';
import { authenticated } from './core/authenticated.guard';
import { skipApplicationChoice, skipDirectionChoice } from './core/landing.guards';
import { Home } from './pages/home';

/**
 * Two areas, each scoped to a direction chosen first (a choice skipped when there is only one):
 * the applications (master-detail: direction › application › group) and the users.
 *
 * Route titles are displayed in the browser tab, see PageTitleStrategy.
 */
export const routes: Routes = [
  { path: '', component: Home, title: $localize`:@@page.home:Accueil` },
  {
    path: '',
    canActivateChild: [authenticated],
    children: [
      {
        path: 'applications',
        canActivate: [skipDirectionChoice('applications')],
        loadComponent: () =>
          import('./pages/shared/direction-chooser').then((m) => m.DirectionChooser),
        data: { area: 'applications' },
        title: $localize`:@@page.applications:Applications`,
      },
      {
        path: 'applications/:direction',
        loadComponent: () =>
          import('./pages/applications/applications-browser').then((m) => m.ApplicationsBrowser),
        title: $localize`:@@page.applications:Applications`,
        children: [
          {
            path: '',
            canActivate: [skipApplicationChoice],
            loadComponent: () =>
              import('./pages/applications/direction-overview').then((m) => m.DirectionOverview),
          },
          {
            path: ':applicationId',
            loadComponent: () =>
              import('./pages/applications/application-detail').then((m) => m.ApplicationDetail),
            title: $localize`:@@page.application:Application`,
            loadChildren: () =>
              import('./pages/applications/application.routes').then((m) => m.applicationRoutes),
          },
        ],
      },
      {
        path: 'users',
        canActivate: [skipDirectionChoice('users')],
        loadComponent: () =>
          import('./pages/shared/direction-chooser').then((m) => m.DirectionChooser),
        data: { area: 'users' },
        title: $localize`:@@page.users:Utilisateurs`,
      },
      {
        path: 'users/:direction',
        loadComponent: () => import('./pages/users/users').then((m) => m.Users),
        title: $localize`:@@page.users:Utilisateurs`,
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
