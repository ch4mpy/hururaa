import { Component, computed, inject, input } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DirectionsApi, UserResponse } from '@api/hururaa-api';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';
import { of } from 'rxjs';
import { DelegationsService } from '../../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';
import { injectNotifier, userLabel } from '../shared/labels';
import { PermissionHistory } from '../shared/permission-history';
import { UserPicker } from '../shared/user-picker';

/**
 * What the applications area shows of a direction when none of its applications is selected: for
 * its administrators, the direction's own page (its administrators, designated by Hurura'a
 * administrators, and its permission history); for the managers of several of its applications,
 * an invitation to pick one.
 */
@Component({
  selector: 'app-direction-overview',
  imports: [ButtonModule, TableModule, UserPicker, PermissionHistory],
  template: `
    @if (administers()) {
      <section class="mb-5">
        <div class="flex flex-wrap align-items-center justify-content-between gap-3 mb-3">
          <h2 class="text-xl m-0" i18n="@@direction.admins">Administrateurs</h2>
          @if (delegations.isAdmin()) {
            <app-user-picker
              [direction]="direction()"
              inputId="admin-picker"
              i18n-label="@@direction.admin.add"
              label="Désigner administrateur"
              (picked)="addAdmin($event)"
            />
          }
        </div>
        <p-table [value]="admins.value() ?? []" [loading]="admins.isLoading()">
          <ng-template #header>
            <tr>
              <th i18n="@@user.name">Nom</th>
              <th i18n="@@user.email">Courriel</th>
              @if (delegations.isAdmin()) {
                <th><span class="sr-only" i18n="@@actions">Actions</span></th>
              }
            </tr>
          </ng-template>
          <ng-template #body let-admin>
            <tr>
              <td>{{ labelOf(admin) }}</td>
              <td>{{ admin.email }}</td>
              @if (delegations.isAdmin()) {
                <td class="text-right">
                  <p-button
                    icon="ri-user-unfollow-line"
                    severity="danger"
                    [text]="true"
                    i18n-ariaLabel="@@direction.admin.remove"
                    ariaLabel="Retirer cet administrateur"
                    (onClick)="removeAdmin(admin)"
                  />
                </td>
              }
            </tr>
          </ng-template>
          <ng-template #emptymessage>
            <tr>
              <td colspan="3" i18n="@@direction.admins.empty">Aucun administrateur</td>
            </tr>
          </ng-template>
        </p-table>
      </section>

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
  private readonly notify = injectNotifier();
  protected readonly delegations = inject(DelegationsService);
  protected readonly labelOf = userLabel;

  /** Bound from the parent's `:direction` route parameter. */
  readonly direction = input.required<string>();

  protected readonly administers = computed(() =>
    this.delegations.canEditApplicationsOf(this.direction()),
  );

  protected readonly admins = rxResource({
    params: () => (this.administers() ? this.direction() : undefined),
    stream: ({ params }) => (params ? this.api.getDirectionAdmins(params) : of([])),
  });

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.DIRECTION)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => event.tenant === this.direction() && this.admins.reload());
  }

  protected addAdmin(user: UserResponse): void {
    this.api.addDirectionAdmin(this.direction(), user.id).subscribe(() => {
      this.notify(
        $localize`:@@direction.admin.added:${userLabel(user)}:user: désigné administrateur`,
      );
      this.admins.reload();
    });
  }

  protected removeAdmin(user: UserResponse): void {
    this.api.removeDirectionAdmin(this.direction(), user.id).subscribe(() => {
      this.notify(
        $localize`:@@direction.admin.removed:${userLabel(user)}:user: n'est plus administrateur`,
      );
      this.admins.reload();
    });
  }
}
