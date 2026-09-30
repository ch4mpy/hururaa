import { Injectable, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

export const APP_NAME = "Hurura'a";

/**
 * Sets the document title from the routes' `title` and exposes the active page title as a signal
 * for the header to display it.
 *
 * Registered with `{ provide: TitleStrategy, useExisting: PageTitleStrategy }` so that the router
 * and the components injecting `PageTitleStrategy` share the same instance.
 */
@Injectable({ providedIn: 'root' })
export class PageTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  private readonly currentPageTitle = signal<string | undefined>(undefined);

  /** The `title` of the active route, if any. */
  readonly pageTitle = this.currentPageTitle.asReadonly();

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const pageTitle = this.buildTitle(snapshot);
    this.currentPageTitle.set(pageTitle);
    this.title.setTitle(pageTitle ? `${pageTitle} · ${APP_NAME}` : APP_NAME);
  }
}
