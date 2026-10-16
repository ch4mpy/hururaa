import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ApplicationManagersApi, ApplicationsApi } from '@api/hururaa-api';
import { TableModule } from 'primeng/table';
import { map, of } from 'rxjs';
import { HURURAA_CLIENT_PREFIX, productOwnersGroup } from '../../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';
import { userLabel } from '../shared/labels';
import { ApplicationContext } from './application-context';

/**
 * The managers tab of an application: who manages its roles, groups and co-managers. They are the
 * members of its `hururaa.<prefix>.product-owners` group, one of Hurura'a's groups: they are
 * designated there, which this tab links to.
 */
@Component({
  selector: 'app-application-managers',
  imports: [RouterLink, TableModule],
  template: `
    <div class="flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
      <p class="m-0 text-color-secondary" i18n="@@application.managers.intro">
        Les gestionnaires définissent les rôles, les groupes et leurs membres.
      </p>
      @if (hururaa.value(); as app) {
        <a
          [routerLink]="['/applications', direction(), app.id, 'groups', productOwners()]"
          class="flex align-items-center gap-2"
        >
          <i class="ri-group-line" aria-hidden="true"></i>
          <span i18n="@@application.managers.group">Gérer le groupe des gestionnaires</span>
        </a>
      }
    </div>
    <p-table [value]="managers.value() ?? []" [loading]="managers.isLoading()">
      <ng-template #header>
        <tr>
          <th i18n="@@user.name">Nom</th>
          <th i18n="@@user.email">Courriel</th>
        </tr>
      </ng-template>
      <ng-template #body let-manager>
        <tr>
          <td>{{ labelOf(manager) }}</td>
          <td>{{ manager.email }}</td>
        </tr>
      </ng-template>
      <ng-template #emptymessage>
        <tr>
          <td colspan="2" i18n="@@application.managers.empty">Aucun gestionnaire</td>
        </tr>
      </ng-template>
    </p-table>
  `,
})
export class ApplicationManagers {
  private readonly api = inject(ApplicationManagersApi);
  private readonly applicationsApi = inject(ApplicationsApi);
  private readonly context = inject(ApplicationContext);
  protected readonly labelOf = userLabel;

  readonly direction = input.required<string>();
  readonly applicationId = input.required({ transform: numberAttribute });

  protected readonly managers = rxResource({
    params: () => ({ direction: this.direction(), id: this.applicationId() }),
    stream: ({ params }) => this.api.getApplicationManagers(params.direction, params.id),
  });

  /** The application's managers' group, one of Hurura'a's groups in the application's direction. */
  protected readonly productOwners = computed(() => {
    const clientPrefix = this.context.application.value()?.clientPrefix;
    return clientPrefix ? productOwnersGroup(clientPrefix) : undefined;
  });

  /** Hurura'a, available in every direction, the application the managers' group belongs to. */
  protected readonly hururaa = rxResource({
    params: () => (this.productOwners() ? this.direction() : undefined),
    stream: ({ params }) =>
      params
        ? this.applicationsApi
            .getApplications(params)
            .pipe(map((apps) => apps.find((a) => a.clientPrefix === HURURAA_CLIENT_PREFIX)))
        : of(undefined),
  });

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.APPLICATION)
      .pipe(takeUntilDestroyed())
      .subscribe(
        (event) => event.resourceId === String(this.applicationId()) && this.managers.reload(),
      );
  }
}
