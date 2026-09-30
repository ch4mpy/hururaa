import { Routes } from '@angular/router';
import { authenticated } from './core/authenticated.guard';
import { Home } from './pages/home';

// Route titles are displayed in the browser tab, see PageTitleStrategy
export const routes: Routes = [
  { path: '', component: Home, title: $localize`:@@page.home:Accueil` },
  {
    path: '',
    canActivateChild: [authenticated],
    children: [
      {
        path: 'applications',
        loadComponent: () => import('./pages/applications').then((m) => m.Applications),
        title: $localize`:@@page.applications:Applications`,
      },
      {
        path: 'applications/:applicationId',
        loadComponent: () =>
          import('./pages/application-detail').then((m) => m.ApplicationDetail),
        title: $localize`:@@page.application:Application`,
      },
      {
        path: 'directions',
        loadComponent: () => import('./pages/directions').then((m) => m.Directions),
        title: $localize`:@@page.directions:Directions`,
      },
      {
        path: 'directions/:direction',
        loadComponent: () => import('./pages/direction-detail').then((m) => m.DirectionDetail),
        title: $localize`:@@page.direction:Direction`,
      },
      {
        path: 'directions/:direction/groups/:group',
        loadComponent: () => import('./pages/group-detail').then((m) => m.GroupDetail),
        title: $localize`:@@page.group:Groupe`,
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
