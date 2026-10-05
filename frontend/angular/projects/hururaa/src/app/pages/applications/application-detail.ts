import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { TabsModule } from 'primeng/tabs';
import { filter, map } from 'rxjs';
import { ApplicationContext } from './application-context';

interface ApplicationTab {
  path: string;
  label: string;
  icon: string;
}

/**
 * An application of a direction (for good: it never changes direction), as tabs (see
 * `applicationRoutes`): its groups, its roles, its managers, its permission history and, for its
 * direction's administrators, its settings. The tabs share the application through the
 * `ApplicationContext` provided here.
 */
@Component({
  selector: 'app-application-detail',
  imports: [RouterLink, RouterOutlet, TabsModule],
  providers: [ApplicationContext],
  template: `
    @if (context.application.value(); as app) {
      @if (app.direction !== direction()) {
        <p i18n="@@application.otherDirection">
          Cette application n'est pas rattachée à cette direction.
        </p>
      } @else if (!context.canManage()) {
        <p i18n="@@application.noSay">
          Les rôles et les gestionnaires de cette application ne sont visibles que de ses
          gestionnaires et des administrateurs de sa direction.
        </p>
      } @else {
        <header class="flex flex-wrap align-items-baseline gap-3 mb-2">
          <h2 class="text-2xl m-0">{{ app.name }}</h2>
          <span class="text-color-secondary">
            <code>{{ app.bffClientId }}</code> · <code>{{ app.apiClientId }}</code>
          </span>
        </header>
        <p-tabs [value]="tab()" [scrollable]="true">
          <p-tablist>
            @for (tab of tabs(); track tab.path) {
              <p-tab
                [value]="tab.path"
                [routerLink]="tab.path"
                class="flex align-items-center gap-2"
              >
                <i [class]="tab.icon" aria-hidden="true"></i>
                <span>{{ tab.label }}</span>
              </p-tab>
            }
          </p-tablist>
        </p-tabs>
        <div class="pt-4">
          <router-outlet />
        </div>
      }
    }
  `,
})
export class ApplicationDetail {
  protected readonly context = inject(ApplicationContext);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Bound from the parent's `:direction` route parameter. */
  readonly direction = input.required<string>();

  /** Bound from the `:applicationId` route parameter. */
  readonly applicationId = input.required({ transform: numberAttribute });

  protected readonly tabs = computed<ApplicationTab[]>(() => [
    { path: 'groups', label: $localize`:@@application.tab.groups:Groupes`, icon: 'ri-group-line' },
    {
      path: 'roles',
      label: $localize`:@@application.tab.roles:Rôles`,
      icon: 'ri-shield-keyhole-line',
    },
    {
      path: 'managers',
      label: $localize`:@@application.tab.managers:Gestionnaires`,
      icon: 'ri-user-star-line',
    },
    {
      path: 'history',
      label: $localize`:@@application.tab.history:Historique`,
      icon: 'ri-history-line',
    },
    ...(this.context.canEdit()
      ? [
          {
            path: 'settings',
            label: $localize`:@@application.tab.settings:Paramètres`,
            icon: 'ri-settings-3-line',
          },
        ]
      : []),
  ]);

  /** The first segment of the child route: a group's page is in the groups tab. */
  protected readonly tab = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.currentTab()),
    ),
    { initialValue: this.currentTab() },
  );

  constructor() {
    this.context.bind(this.direction, this.applicationId);
  }

  /**
   * Read from this route's snapshot tree, complete from the start: the child `ActivatedRoute`'s
   * own snapshot is only set once the child is activated, after this component is created.
   */
  private currentTab(): string {
    return this.route.snapshot.firstChild?.url[0]?.path ?? 'groups';
  }
}
