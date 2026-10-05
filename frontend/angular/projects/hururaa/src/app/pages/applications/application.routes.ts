import { Routes } from '@angular/router';

/** The tabs of an application (`ApplicationDetail`), groups first: what is managed most often. */
export const applicationRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'groups' },
  {
    path: 'groups',
    loadComponent: () => import('./application-groups').then((m) => m.ApplicationGroups),
  },
  {
    path: 'groups/:group',
    loadComponent: () => import('./group-detail').then((m) => m.GroupDetail),
    title: $localize`:@@page.group:Groupe`,
  },
  {
    path: 'roles',
    loadComponent: () => import('./application-roles').then((m) => m.ApplicationRoles),
  },
  {
    path: 'managers',
    loadComponent: () => import('./application-managers').then((m) => m.ApplicationManagers),
  },
  {
    path: 'history',
    loadComponent: () => import('./application-history').then((m) => m.ApplicationHistory),
  },
  {
    path: 'settings',
    loadComponent: () => import('./application-settings').then((m) => m.ApplicationSettings),
  },
];
