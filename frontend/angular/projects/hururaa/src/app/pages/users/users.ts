import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { rxResource, takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ApplicationRolesApi, ApplicationsApi, DirectionsApi, GroupsApi } from '@api/hururaa-api';
import { PfPageComponent } from 'pf-ui';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { debounceTime, merge, of } from 'rxjs';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';
import { DirectionSwitch } from '../shared/direction-switch';
import { userLabel } from '../shared/labels';
import { UserGroups } from './user-groups';

const PAGE_SIZE = 20;

/**
 * The users area of a direction: its members searched by name, username or e-mail, and narrowed
 * to the members of the groups of an application, of one group, or of the groups granting a role.
 * Each user's groups unfold in their row.
 */
@Component({
  selector: 'app-users',
  imports: [
    PfPageComponent,
    FormsModule,
    ButtonModule,
    IconFieldModule,
    InputIconModule,
    InputTextModule,
    SelectModule,
    TableModule,
    DirectionSwitch,
    UserGroups,
  ],
  template: `
    <pf-page [withPadding]="true">
      <ng-template #title>
        <span i18n="@@users.title">Utilisateurs</span>
      </ng-template>
      <ng-template #toolbar>
        <app-direction-switch area="users" [direction]="direction()" />
      </ng-template>

      <div class="flex flex-wrap align-items-end gap-3 mb-3" role="search">
        <div class="flex flex-column gap-1 flex-grow-1">
          <label for="users-search" i18n="@@users.search">Nom, identifiant ou courriel</label>
          <p-iconfield>
            <p-inputicon styleClass="ri-search-line" />
            <input
              pInputText
              id="users-search"
              class="w-full"
              [ngModel]="searchInput()"
              (ngModelChange)="searchInput.set($event)"
            />
          </p-iconfield>
        </div>
        <div class="flex flex-column gap-1">
          <label for="users-application" i18n="@@users.application">Application</label>
          <p-select
            inputId="users-application"
            [options]="applications.value() ?? []"
            optionLabel="name"
            optionValue="id"
            [ngModel]="applicationId()"
            (ngModelChange)="selectApplication($event)"
            [showClear]="true"
            i18n-placeholder="@@users.application.all"
            placeholder="Toutes"
          />
        </div>
        <div class="flex flex-column gap-1">
          <label for="users-group" i18n="@@users.group">Groupe</label>
          <p-select
            inputId="users-group"
            [options]="groupOptions()"
            optionLabel="name"
            optionValue="name"
            [ngModel]="group()"
            (ngModelChange)="narrow(group, $event)"
            [showClear]="true"
            [filter]="true"
            i18n-placeholder="@@users.group.all"
            placeholder="Tous"
          />
        </div>
        <div class="flex flex-column gap-1">
          <label for="users-role" i18n="@@users.role">Rôle</label>
          <p-select
            inputId="users-role"
            [options]="roles.value() ?? []"
            optionLabel="name"
            optionValue="name"
            [ngModel]="role()"
            (ngModelChange)="narrow(role, $event)"
            [showClear]="true"
            [disabled]="!applicationId()"
            i18n-placeholder="@@users.role.pickApplication"
            placeholder="Choisir une application"
          />
        </div>
      </div>

      <p-table
        [value]="users.value()?.content ?? []"
        dataKey="id"
        [lazy]="true"
        [paginator]="true"
        [rows]="pageSize"
        [first]="page() * pageSize"
        [totalRecords]="users.value()?.page?.totalElements ?? 0"
        [loading]="users.isLoading()"
        (onLazyLoad)="load($event)"
      >
        <ng-template #header>
          <tr>
            <th class="w-3rem"><span class="sr-only" i18n="@@users.groups">Groupes</span></th>
            <th i18n="@@user.name">Nom</th>
            <th i18n="@@user.username">Identifiant</th>
            <th i18n="@@user.email">Courriel</th>
          </tr>
        </ng-template>
        <ng-template #body let-user let-expanded="expanded">
          <tr>
            <td>
              <p-button
                [pRowToggler]="user"
                [text]="true"
                [rounded]="true"
                severity="secondary"
                [icon]="expanded ? 'ri-arrow-down-s-line' : 'ri-arrow-right-s-line'"
                i18n-ariaLabel="@@users.showGroups"
                ariaLabel="Afficher ses groupes"
              />
            </td>
            <td>{{ labelOf(user) }}</td>
            <td>{{ user.username }}</td>
            <td>{{ user.email }}</td>
          </tr>
        </ng-template>
        <ng-template #expandedrow let-user>
          <tr>
            <td></td>
            <td colspan="3">
              <app-user-groups [direction]="direction()" [userId]="user.id" />
            </td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr>
            <td colspan="4" i18n="@@users.empty">Aucun utilisateur ne correspond</td>
          </tr>
        </ng-template>
      </p-table>
    </pf-page>
  `,
})
export class Users {
  private readonly directionsApi = inject(DirectionsApi);
  private readonly applicationsApi = inject(ApplicationsApi);
  private readonly groupsApi = inject(GroupsApi);
  private readonly rolesApi = inject(ApplicationRolesApi);
  protected readonly labelOf = userLabel;
  protected readonly pageSize = PAGE_SIZE;

  /** Bound from the `:direction` route parameter. */
  readonly direction = input.required<string>();

  protected readonly searchInput = signal('');
  private readonly search = toSignal(toObservable(this.searchInput).pipe(debounceTime(300)), {
    initialValue: '',
  });
  protected readonly applicationId = signal<number | undefined>(undefined);
  protected readonly group = signal<string | undefined>(undefined);
  protected readonly role = signal<string | undefined>(undefined);
  protected readonly page = signal(0);

  /** The applications whose roles the user can read: those they manage. */
  protected readonly applications = rxResource({
    params: () => this.direction(),
    stream: ({ params }) => this.applicationsApi.getApplications(params, true),
  });

  private readonly groups = rxResource({
    params: () => this.direction(),
    stream: ({ params }) => this.groupsApi.getGroups(params),
  });

  /** The selected application's groups, all of the direction's otherwise. */
  protected readonly groupOptions = computed(() => {
    const groups = this.groups.value() ?? [];
    const applicationId = this.applicationId();
    return applicationId ? groups.filter((g) => g.applicationId === applicationId) : groups;
  });

  protected readonly roles = rxResource({
    params: () => {
      const id = this.applicationId();
      return id ? { direction: this.direction(), id } : undefined;
    },
    stream: ({ params }) =>
      params ? this.rolesApi.getApplicationRoles(params.direction, params.id) : of([]),
  });

  protected readonly users = rxResource({
    params: () => ({
      direction: this.direction(),
      search: this.search(),
      group: this.group(),
      applicationId: this.applicationId(),
      role: this.role(),
      page: this.page(),
    }),
    stream: ({ params }) =>
      this.directionsApi.getDirectionUsers(
        params.direction,
        params.search,
        params.group,
        params.applicationId,
        params.role,
        params.page,
        PAGE_SIZE,
      ),
  });

  constructor() {
    // filters are a direction's: cleared when switching to another one
    effect(() => {
      this.direction();
      untracked(() => {
        this.searchInput.set('');
        this.selectApplication(undefined);
      });
    });
    // back to the first page whenever the search changes
    toObservable(this.search)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.page.set(0));
    const events = inject(ResourceEventsService);
    merge(events.of(ResourceTypes.GROUP), events.of(ResourceTypes.DIRECTION))
      .pipe(takeUntilDestroyed())
      .subscribe((event) => {
        if (event.tenant === this.direction()) {
          this.groups.reload();
          this.users.reload();
        }
      });
  }

  /** Narrowing to an application clears the group and role of another one. */
  protected selectApplication(applicationId: number | undefined): void {
    this.applicationId.set(applicationId ?? undefined);
    this.role.set(undefined);
    if (!this.groupOptions().some((g) => g.name === this.group())) {
      this.group.set(undefined);
    }
    this.page.set(0);
  }

  protected narrow(filter: typeof this.group, value: string | undefined): void {
    filter.set(value ?? undefined);
    this.page.set(0);
  }

  protected load(event: TableLazyLoadEvent): void {
    this.page.set(Math.floor((event.first ?? 0) / PAGE_SIZE));
  }
}
