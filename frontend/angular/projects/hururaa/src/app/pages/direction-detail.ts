import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApplicationsApi, DirectionsApi, GroupsApi, UserResponse } from '@api/hururaa-api';
import { PfPageComponent } from 'pf-ui';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { of } from 'rxjs';
import { DelegationsService } from '../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../core/resource-events.service';
import { injectNotifier, userLabel } from './shared/labels';
import { PermissionHistory } from './shared/permission-history';
import { UserPicker } from './shared/user-picker';

const PAGE_SIZE = 10;

/**
 * A direction: its administrators (designated by Hurura'a administrators), its applications, its
 * groups (each belonging to one of its applications) and its members.
 */
@Component({
  selector: 'app-direction-detail',
  imports: [
    PfPageComponent,
    RouterLink,
    FormsModule,
    ButtonModule,
    InputTextModule,
    TableModule,
    UserPicker,
    PermissionHistory,
  ],
  template: `
    <pf-page [withPadding]="true">
      <ng-template #title>{{ direction().toUpperCase() }}</ng-template>

      <h2 i18n="@@direction.applications">Applications</h2>
      <ul>
        @for (application of applications.value() ?? []; track application.id) {
          <li>
            <a [routerLink]="['/applications', application.id]">{{ application.name }}</a>
          </li>
        } @empty {
          <li i18n="@@direction.applications.empty">Aucune application rattachée</li>
        }
      </ul>

      @if (!canRead()) {
        <p i18n="@@direction.noSay">
          Les administrateurs, groupes et membres d'une direction ne sont visibles que de ses
          administrateurs et des gestionnaires de ses applications.
        </p>
      } @else {
        <h2 i18n="@@direction.admins">Administrateurs</h2>
        <p-table [value]="admins.value() ?? []" [loading]="admins.isLoading()">
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
        @if (delegations.isAdmin()) {
          <div class="mt-3">
            <app-user-picker
              [direction]="direction()"
              inputId="admin-picker"
              i18n-label="@@direction.admin.add"
              label="Désigner administrateur"
              (picked)="addAdmin($event)"
            />
          </div>
        }

        <h2 i18n="@@direction.groups">Groupes</h2>
        <ul>
          @for (group of groups.value() ?? []; track group.id) {
            <li>
              <a [routerLink]="['/directions', direction(), 'groups', group.name]">{{
                group.name
              }}</a>
              @if (group.applicationId) {
                ·
                <a [routerLink]="['/applications', group.applicationId]">{{
                  group.applicationName
                }}</a>
              }
            </li>
          } @empty {
            <li i18n="@@direction.groups.empty">Aucun groupe</li>
          }
        </ul>
        <small class="block" i18n="@@direction.groups.hint"
          >Chaque groupe appartient à une application, dont il porte le préfixe : il se crée depuis
          la page de cette application.</small
        >

        <h2 i18n="@@direction.members">Membres</h2>
        <div class="flex align-items-center gap-2 mb-2">
          <label for="memberSearch" i18n="@@direction.members.search">Rechercher</label>
          <input
            pInputText
            id="memberSearch"
            [ngModel]="search()"
            (ngModelChange)="searchMembers($event)"
          />
        </div>
        <p-table
          [value]="members.value()?.content ?? []"
          [lazy]="true"
          [paginator]="true"
          [rows]="pageSize"
          [totalRecords]="members.value()?.page?.totalElements ?? 0"
          [loading]="members.isLoading()"
          (onLazyLoad)="loadMembers($event)"
        >
          <ng-template #header>
            <tr>
              <th i18n="@@user.name">Nom</th>
              <th i18n="@@user.username">Identifiant</th>
              <th i18n="@@user.email">Courriel</th>
            </tr>
          </ng-template>
          <ng-template #body let-member>
            <tr>
              <td>{{ labelOf(member) }}</td>
              <td>{{ member.username }}</td>
              <td>{{ member.email }}</td>
            </tr>
          </ng-template>
        </p-table>

        <h2 i18n="@@direction.history">Historique des permissions</h2>
        <app-permission-history [direction]="direction()" />
      }
    </pf-page>
  `,
})
export class DirectionDetail {
  private readonly directionsApi = inject(DirectionsApi);
  private readonly groupsApi = inject(GroupsApi);
  private readonly applicationsApi = inject(ApplicationsApi);
  private readonly notify = injectNotifier();
  protected readonly delegations = inject(DelegationsService);
  protected readonly labelOf = userLabel;
  protected readonly pageSize = PAGE_SIZE;

  /** Bound from the `:direction` route parameter. */
  readonly direction = input.required<string>();

  /**
   * Mirrors the API's rule for reading a direction: Hurura'a administrator, administrator of the
   * direction or manager of one of its applications.
   */
  protected readonly canRead = computed(() => this.delegations.canReadDirection(this.direction()));

  protected readonly applications = rxResource({
    params: () => this.direction(),
    stream: ({ params }) => this.applicationsApi.getApplications(params),
  });

  protected readonly admins = rxResource({
    params: () => (this.canRead() ? this.direction() : undefined),
    stream: ({ params }) => (params ? this.directionsApi.getDirectionAdmins(params) : of([])),
  });

  protected readonly groups = rxResource({
    params: () => (this.canRead() ? this.direction() : undefined),
    stream: ({ params }) => (params ? this.groupsApi.getGroups(params) : of([])),
  });

  protected readonly search = signal('');
  private readonly page = signal(0);

  protected readonly members = rxResource({
    params: () =>
      this.canRead()
        ? { direction: this.direction(), search: this.search(), page: this.page() }
        : undefined,
    stream: ({ params }) =>
      params
        ? this.directionsApi.getDirectionUsers(
            params.direction,
            params.search,
            params.page,
            PAGE_SIZE,
          )
        : of({ content: [] }),
  });

  constructor() {
    const events = inject(ResourceEventsService);
    events
      .of(ResourceTypes.DIRECTION)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => event.tenant === this.direction() && this.admins.reload());
    events
      .of(ResourceTypes.GROUP)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => event.tenant === this.direction() && this.groups.reload());
    events
      .of(ResourceTypes.APPLICATION)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.applications.reload());
  }

  protected searchMembers(search: string): void {
    this.page.set(0);
    this.search.set(search);
  }

  protected loadMembers(event: TableLazyLoadEvent): void {
    this.page.set(Math.floor((event.first ?? 0) / PAGE_SIZE));
  }

  protected addAdmin(user: UserResponse): void {
    this.directionsApi.addDirectionAdmin(this.direction(), user.id).subscribe(() => {
      this.notify(
        $localize`:@@direction.admin.added:${userLabel(user)}:user: désigné administrateur`,
      );
      this.admins.reload();
    });
  }

  protected removeAdmin(user: UserResponse): void {
    this.directionsApi.removeDirectionAdmin(this.direction(), user.id).subscribe(() => {
      this.notify(
        $localize`:@@direction.admin.removed:${userLabel(user)}:user: n'est plus administrateur`,
      );
      this.admins.reload();
    });
  }
}
