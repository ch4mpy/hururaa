import { DOCUMENT } from '@angular/common';
import { InjectionToken, inject } from '@angular/core';

/**
 * The browser `Location`, injectable so that services triggering a real navigation (login and
 * logout redirections) can be unit-tested without touching `window.location`.
 */
export const LOCATION = new InjectionToken<Location>('LOCATION', {
  providedIn: 'root',
  factory: () => inject(DOCUMENT).location,
});
