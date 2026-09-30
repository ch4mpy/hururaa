import { NgTemplateOutlet } from '@angular/common';
import { Component, TemplateRef, contentChild, input } from '@angular/core';

/**
 * Stands in for pf-ui's `PfPageComponent` in specs: pf-ui is a single module importing all of its
 * dependencies, some of which can't load in Node/jsdom (CommonJS named imports, canvas-based image
 * libraries). Use with `vi.mock('pf-ui', ...)`.
 *
 * Its content queries are pf-ui's own (`contentChild('title')` and `contentChild('toolbar')`, read
 * in `pf-ui.mjs`): a page's `#title` / `#toolbar` templates are matched exactly as they would be,
 * including against the `#title` templates of cards nested in the page.
 */
@Component({
  // pf-ui's selector, which the pages use: this stub replaces pf-page, it can't be prefixed
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'pf-page',
  imports: [NgTemplateOutlet],
  template: `
    @if (title(); as title) {
      <h1><ng-container *ngTemplateOutlet="title" /></h1>
    }
    @if (toolbar(); as toolbar) {
      <div><ng-container *ngTemplateOutlet="toolbar" /></div>
    }
    <ng-content />
  `,
})
export class PfPageStub {
  readonly withPadding = input(false);
  readonly title = contentChild<TemplateRef<unknown>>('title');
  readonly toolbar = contentChild<TemplateRef<unknown>>('toolbar');
}
