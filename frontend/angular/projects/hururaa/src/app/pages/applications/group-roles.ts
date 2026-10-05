import { Component, inject, input } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { GroupsApi } from '@api/hururaa-api';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';

/** The roles a group grants, read-only, as a line of tags (in the groups tab's table). */
@Component({
  selector: 'app-group-roles',
  template: `
    <span class="flex flex-wrap gap-1">
      @for (role of roles.value() ?? []; track role.role) {
        <code class="px-2 py-1 border-round surface-100">{{ role.role }}</code>
      } @empty {
        @if (!roles.isLoading()) {
          <span class="text-color-secondary" i18n="@@group.roles.none">Aucun rôle</span>
        }
      }
    </span>
  `,
})
export class GroupRoles {
  private readonly api = inject(GroupsApi);

  readonly direction = input.required<string>();
  readonly group = input.required<string>();

  protected readonly roles = rxResource({
    params: () => ({ direction: this.direction(), group: this.group() }),
    stream: ({ params }) => this.api.getGroupRoles(params.direction, params.group),
  });

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.GROUP)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => {
        if (event.tenant === this.direction() && event.resourceId === this.group()) {
          this.roles.reload();
        }
      });
  }
}
