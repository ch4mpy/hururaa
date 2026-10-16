import { Component, computed, inject, input } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ApplicationsApi, DirectionsApi } from '@api/hururaa-api';
import { TableModule } from 'primeng/table';
import { map, of } from 'rxjs';
import {
  ADMINS_GROUP,
  DelegationsService,
  HURURAA_CLIENT_PREFIX,
} from '../../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';
import { userLabel } from '../shared/labels';
import { PermissionHistory } from '../shared/permission-history';

/**
 * What the applications area shows of a direction when none of its applications is selected: for
 * its delegates, its administrators (the members of its `hururaa.admins` group, one of Hurura'a's
 * groups, designated there, which this page links to); for its administrators, its permission
 * history too; for the managers of its applications, an invitation to pick one.
 */
@Component({
  selector: 'app-direction-overview',
  imports: [RouterLink, TableModule, PermissionHistory],
  template: `
    @if (reads()) {
      <section class="mb-5">
        <div class="flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
          <h2 class="text-xl m-0" i18n="@@direction.admins">Administrateurs</h2>
          @if (hururaa.value(); as app) {
            <a
              [routerLink]="['/applications', direction(), app.id, 'groups', adminsGroup]"
              class="flex align-items-center gap-2"
            >
              <i class="ri-group-line" aria-hidden="true"></i>
              <span i18n="@@direction.admins.group">Gérer le groupe des administrateurs</span>
            </a>
          }
        </div>
        <p-table [value]="admins.value() ?? []" [loading]="admins.isLoading()">
          <ng-template #header>
            <tr>
              <th i18n="@@user.name">Nom</th>
              <th i18n="@@user.email">Courriel</th>
            </tr>
          </ng-template>
          <ng-template #body let-admin>
            <tr>
              <td>{{ labelOf(admin) }}</td>
              <td>{{ admin.email }}</td>
            </tr>
          </ng-template>
          <ng-template #emptymessage>
            <tr>
              <td colspan="2" i18n="@@direction.admins.empty">Aucun administrateur</td>
            </tr>
          </ng-template>
        </p-table>
      </section>
    }
    @if (administers()) {
      <section>
        <h2 class="text-xl mt-0 mb-3" i18n="@@direction.history">Historique des permissions</h2>
        <app-permission-history [direction]="direction()" />
      </section>
    } @else {
      <div class="flex flex-column align-items-center gap-2 p-5 text-color-secondary text-center">
        <i class="ri-apps-2-line text-5xl" aria-hidden="true"></i>
        <p class="m-0" i18n="@@direction.pickApplication">
          Choisissez une des applications que vous gérez.
        </p>
      </div>
    }
  `,
})
export class DirectionOverview {
  private readonly api = inject(DirectionsApi);
  private readonly applicationsApi = inject(ApplicationsApi);
  protected readonly delegations = inject(DelegationsService);
  protected readonly labelOf = userLabel;
  protected readonly adminsGroup = ADMINS_GROUP;

  /** Bound from the parent's `:direction` route parameter. */
  readonly direction = input.required<string>();

  protected readonly administers = computed(() =>
    this.delegations.canEditApplicationsOf(this.direction()),
  );

  /** Mirrors the access rule of the API's direction administrators endpoint. */
  protected readonly reads = computed(() => this.delegations.canReadDirection(this.direction()));

  protected readonly admins = rxResource({
    params: () => (this.reads() ? this.direction() : undefined),
    stream: ({ params }) => (params ? this.api.getDirectionAdmins(params) : of([])),
  });

  /** Hurura'a, available in every direction: the administrators' group is one of its groups. */
  protected readonly hururaa = rxResource({
    params: () => (this.reads() ? this.direction() : undefined),
    stream: ({ params }) =>
      params
        ? this.applicationsApi
            .getApplications(params)
            .pipe(map((apps) => apps.find((a) => a.clientPrefix === HURURAA_CLIENT_PREFIX)))
        : of(undefined),
  });

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.DIRECTION)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => event.tenant === this.direction() && this.admins.reload());
  }
}
