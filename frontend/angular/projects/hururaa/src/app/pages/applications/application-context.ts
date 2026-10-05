import { Injectable, Signal, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApplicationRolesApi, ApplicationsApi } from '@api/hururaa-api';
import { of } from 'rxjs';
import { DelegationsService } from '../../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';

/**
 * The application whose tabs are displayed, provided by `ApplicationDetail` to them: the
 * application itself, its roles (listed in a tab, granted by its groups in another) and what the
 * user may do with it, loaded once for all tabs.
 */
@Injectable()
export class ApplicationContext {
  private readonly applicationsApi = inject(ApplicationsApi);
  private readonly rolesApi = inject(ApplicationRolesApi);
  private readonly delegations = inject(DelegationsService);

  private readonly route = signal<{ direction: Signal<string>; id: Signal<number> } | undefined>(
    undefined,
  );

  /** The direction of the URL (the application is checked to belong to it). */
  readonly direction = computed(() => this.route()?.direction() ?? '');

  readonly id = computed(() => this.route()?.id());

  readonly application = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.applicationsApi.getApplication(params),
  });

  /** Mirrors the access rule of the API's application roles, groups and managers endpoints. */
  readonly canManage = computed(() => {
    const id = this.id();
    return !!id && this.delegations.canManageApplication({ id, direction: this.direction() });
  });

  /** Mirrors the access rule of the API's application update and delete endpoints. */
  readonly canEdit = computed(() => this.delegations.canEditApplicationsOf(this.direction()));

  readonly roles = rxResource({
    params: () => (this.canManage() ? { direction: this.direction(), id: this.id() } : undefined),
    stream: ({ params }) =>
      params?.id ? this.rolesApi.getApplicationRoles(params.direction, params.id) : of([]),
  });

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.APPLICATION)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => {
        if (event.resourceId === String(this.id())) {
          this.application.reload();
          this.roles.reload();
        }
      });
  }

  /** Called by `ApplicationDetail` with its route-bound inputs. */
  bind(direction: Signal<string>, id: Signal<number>): void {
    this.route.set({ direction, id });
  }
}
