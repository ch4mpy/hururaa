import { Component, input } from '@angular/core';

/**
 * Stands in for pf-ui's `PfTagComponent` in specs (pf-ui can't load in jsdom, see `PfPageStub`):
 * renders its value. Use with `vi.mock('pf-ui', ...)`.
 */
@Component({
  // pf-ui's selector, which the pages use: this stub replaces pf-tag, it can't be prefixed
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'pf-tag',
  template: `<span>{{ value() }}</span>`,
})
export class PfTagStub {
  readonly value = input<string>();
  readonly severity = input<string>();
}
