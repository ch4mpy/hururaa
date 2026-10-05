import { Component, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { DirectionsApi, GroupResponse } from '@api/hururaa-api';
import { DelegationsService } from '../../core/delegations.service';

/**
 * The groups a member of a direction belongs to (their delegations aside), each linked to its
 * page in the applications area when the user manages its application.
 */
@Component({
  selector: 'app-user-groups',
  imports: [RouterLink],
  template: `
    <div class="flex flex-wrap align-items-center gap-2 py-1">
      <span class="text-color-secondary" i18n="@@user.groups">Groupes :</span>
      @for (group of groups.value() ?? []; track group.id) {
        @if (canOpen(group)) {
          <a
            class="px-2 py-1 border-1 surface-border border-round no-underline"
            [routerLink]="['/applications', direction(), group.applicationId, 'groups', group.name]"
            >{{ group.name }}</a
          >
        } @else {
          <span class="px-2 py-1 border-1 surface-border border-round">{{ group.name }}</span>
        }
      } @empty {
        @if (!groups.isLoading()) {
          <span i18n="@@user.groups.empty">aucun</span>
        }
      }
    </div>
  `,
})
export class UserGroups {
  private readonly api = inject(DirectionsApi);
  private readonly delegations = inject(DelegationsService);

  readonly direction = input.required<string>();
  readonly userId = input.required<string>();

  protected readonly groups = rxResource({
    params: () => ({ direction: this.direction(), userId: this.userId() }),
    stream: ({ params }) => this.api.getDirectionUserGroups(params.direction, params.userId),
  });

  protected canOpen(group: GroupResponse): boolean {
    return (
      !!group.applicationId &&
      this.delegations.canManageApplication({
        id: group.applicationId,
        direction: this.direction(),
      })
    );
  }
}
