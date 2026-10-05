import { Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DirectionsApi } from '@api/hururaa-api';
import { SelectModule } from 'primeng/select';
import { DelegationsService } from '../../core/delegations.service';
import { Area } from '../../core/landing.guards';

/**
 * Switches an area to another direction, for users having a say on several of them (hidden
 * otherwise). Shared by the toolbars of both areas.
 */
@Component({
  selector: 'app-direction-switch',
  imports: [FormsModule, SelectModule],
  template: `
    @if (directions().length > 1) {
      <p-select
        [options]="directions()"
        optionLabel="name"
        optionValue="alias"
        [ngModel]="direction()"
        (ngModelChange)="switchTo($event)"
        i18n-ariaLabel="@@directionSwitch.label"
        ariaLabel="Direction"
      />
    }
  `,
})
export class DirectionSwitch {
  private readonly router = inject(Router);
  private readonly delegations = inject(DelegationsService);
  private readonly api = inject(DirectionsApi);

  readonly area = input.required<Area>();

  /** The current direction's alias. */
  readonly direction = input.required<string>();

  private readonly all = rxResource({ stream: () => this.api.getDirections() });

  protected readonly directions = computed(() => this.delegations.withSay(this.all.value() ?? []));

  protected switchTo(direction: string): void {
    if (direction !== this.direction()) {
      void this.router.navigate(['/', this.area(), direction]);
    }
  }
}
