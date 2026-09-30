import { Injectable, inject, signal } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs';

/**
 * The message shown in the shell's error banner (see `ErrorBanner`), fed by `httpErrorInterceptor`.
 *
 * Only the latest message is kept: a new failure replaces the previous one. The banner is cleared
 * when the user dismisses it or navigates to another page (a stale error would otherwise stay on
 * screen after leaving the page that raised it).
 */
@Injectable({ providedIn: 'root' })
export class ErrorBannerService {
  private readonly current = signal<string | undefined>(undefined);

  /** The message to display, `undefined` when there is nothing to show. */
  readonly message = this.current.asReadonly();

  constructor() {
    inject(Router)
      .events.pipe(filter((event) => event instanceof NavigationStart))
      .subscribe(() => this.dismiss());
  }

  show(message: string): void {
    this.current.set(message);
  }

  dismiss(): void {
    this.current.set(undefined);
  }
}
