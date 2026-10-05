import { Component, inject, input, numberAttribute } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApplicationManagersApi, UserResponse } from '@api/hururaa-api';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { DelegationsService } from '../../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';
import { injectNotifier, userLabel } from '../shared/labels';
import { UserPicker } from '../shared/user-picker';

/**
 * The managers tab of an application: who manages its roles, groups and co-managers, designated
 * by its direction's administrators and by its managers.
 */
@Component({
  selector: 'app-application-managers',
  imports: [ButtonModule, TableModule, UserPicker],
  template: `
    <div class="flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
      <p class="m-0 text-color-secondary" i18n="@@application.managers.intro">
        Les gestionnaires définissent les rôles, les groupes et leurs membres.
      </p>
      <app-user-picker
        [direction]="direction()"
        inputId="manager-picker"
        i18n-label="@@application.manager.add"
        label="Désigner gestionnaire"
        (picked)="add($event)"
      />
    </div>
    <p-table [value]="managers.value() ?? []" [loading]="managers.isLoading()">
      <ng-template #header>
        <tr>
          <th i18n="@@user.name">Nom</th>
          <th i18n="@@user.email">Courriel</th>
          <th><span class="sr-only" i18n="@@actions">Actions</span></th>
        </tr>
      </ng-template>
      <ng-template #body let-manager>
        <tr>
          <td>{{ labelOf(manager) }}</td>
          <td>{{ manager.email }}</td>
          <td class="text-right">
            <p-button
              icon="ri-user-unfollow-line"
              severity="danger"
              [text]="true"
              i18n-ariaLabel="@@application.manager.remove"
              ariaLabel="Retirer ce gestionnaire"
              (onClick)="remove(manager)"
            />
          </td>
        </tr>
      </ng-template>
      <ng-template #emptymessage>
        <tr>
          <td colspan="3" i18n="@@application.managers.empty">Aucun gestionnaire</td>
        </tr>
      </ng-template>
    </p-table>
  `,
})
export class ApplicationManagers {
  private readonly api = inject(ApplicationManagersApi);
  private readonly delegations = inject(DelegationsService);
  private readonly notify = injectNotifier();
  protected readonly labelOf = userLabel;

  readonly direction = input.required<string>();
  readonly applicationId = input.required({ transform: numberAttribute });

  protected readonly managers = rxResource({
    params: () => ({ direction: this.direction(), id: this.applicationId() }),
    stream: ({ params }) => this.api.getApplicationManagers(params.direction, params.id),
  });

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.APPLICATION)
      .pipe(takeUntilDestroyed())
      .subscribe(
        (event) => event.resourceId === String(this.applicationId()) && this.managers.reload(),
      );
  }

  protected add(user: UserResponse): void {
    this.api
      .addApplicationManager(this.direction(), this.applicationId(), user.id)
      .subscribe(() => {
        this.notify(
          $localize`:@@application.manager.added:${userLabel(user)}:user: désigné gestionnaire`,
        );
        this.managers.reload();
        this.delegations.refresh();
      });
  }

  protected remove(user: UserResponse): void {
    this.api
      .removeApplicationManager(this.direction(), this.applicationId(), user.id)
      .subscribe(() => {
        this.notify(
          $localize`:@@application.manager.removed:${userLabel(user)}:user: n'est plus gestionnaire`,
        );
        this.managers.reload();
        this.delegations.refresh();
      });
  }
}
