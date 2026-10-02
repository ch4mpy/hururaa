import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ApplicationsApi,
  DelegationChangeResponse,
  DelegationChangeResponseChangeEnum,
  DelegationChangeResponseDelegationEnum,
  DirectionsApi,
  GroupsApi,
  UserResponse,
} from '@api/hururaa-api';
import { PfPageComponent } from 'pf-ui';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { of } from 'rxjs';
import { DelegationsService } from '../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../core/resource-events.service';
import { injectNotifier, nameLabel, userLabel } from './shared/labels';
import { UserPicker } from './shared/user-picker';

const PAGE_SIZE = 10;

/**
 * A direction: its administrators (designated by Hurura'a administrators), its applications, its
 * groups (created by its application managers) and its members.
 */
@Component({
  selector: 'app-direction-detail',
  imports: [
    DatePipe,
    PfPageComponent,
    RouterLink,
    FormsModule,
    ReactiveFormsModule,
    ButtonModule,
    InputTextModule,
    TableModule,
    UserPicker,
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
          Les administrateurs, groupes et membres d'une direction ne sont visibles que de ceux qui
          ont délégation sur elle.
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
            </li>
          } @empty {
            <li i18n="@@direction.groups.empty">Aucun groupe</li>
          }
        </ul>
        @if (delegations.canCreateGroupsIn(direction())) {
          <form
            [formGroup]="groupForm"
            (ngSubmit)="createGroup()"
            class="flex align-items-end gap-3"
          >
            <div class="flex flex-column gap-1">
              <label for="groupName" i18n="@@direction.group.name">Nom du groupe</label>
              <input pInputText id="groupName" formControlName="name" />
            </div>
            <p-button
              type="submit"
              icon="ri-add-line"
              [disabled]="groupForm.invalid"
              i18n-label="@@direction.group.create"
              label="Créer le groupe"
            />
          </form>
        }

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

        <h2 i18n="@@direction.history">Historique des délégations</h2>
        <p-table
          [value]="history.value()?.content ?? []"
          [lazy]="true"
          [paginator]="true"
          [rows]="pageSize"
          [totalRecords]="history.value()?.page?.totalElements ?? 0"
          [loading]="history.isLoading()"
          (onLazyLoad)="loadHistory($event)"
        >
          <ng-template #header>
            <tr>
              <th i18n="@@direction.history.date">Date</th>
              <th i18n="@@direction.history.author">Par</th>
              <th i18n="@@direction.history.change">Changement</th>
              <th i18n="@@direction.history.delegate">Personne concernée</th>
            </tr>
          </ng-template>
          <ng-template #body let-change>
            <tr>
              <td>{{ change.timestamp | date: 'short' }}</td>
              <td>{{ authorOf(change) }}</td>
              <td>{{ describe(change) }}</td>
              <td>{{ delegateOf(change) }}</td>
            </tr>
          </ng-template>
          <ng-template #emptymessage>
            <tr>
              <td colspan="4" i18n="@@direction.history.empty">Aucun changement enregistré</td>
            </tr>
          </ng-template>
        </p-table>
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

  /** Mirrors the API's rule for reading a direction: Hurura'a administrator or delegate. */
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

  private readonly historyPage = signal(0);

  protected readonly history = rxResource({
    params: () =>
      this.canRead() ? { direction: this.direction(), page: this.historyPage() } : undefined,
    stream: ({ params }) =>
      params
        ? this.directionsApi.getDirectionHistory(params.direction, params.page, PAGE_SIZE)
        : of({ content: [] }),
  });

  protected readonly groupForm = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(/^[a-z0-9][a-z0-9._-]*$/)]],
  });

  constructor() {
    const events = inject(ResourceEventsService);
    events
      .of(ResourceTypes.DIRECTION)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => {
        if (event.tenant === this.direction()) {
          this.admins.reload();
          this.history.reload();
        }
      });
    events
      .of(ResourceTypes.GROUP)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => event.tenant === this.direction() && this.groups.reload());
    events
      .of(ResourceTypes.APPLICATION)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => {
        this.applications.reload();
        if (event.tenant === this.direction()) {
          this.history.reload();
        }
      });
  }

  protected searchMembers(search: string): void {
    this.page.set(0);
    this.search.set(search);
  }

  protected loadMembers(event: TableLazyLoadEvent): void {
    this.page.set(Math.floor((event.first ?? 0) / PAGE_SIZE));
  }

  protected loadHistory(event: TableLazyLoadEvent): void {
    this.historyPage.set(Math.floor((event.first ?? 0) / PAGE_SIZE));
  }

  protected authorOf(change: DelegationChangeResponse): string {
    return change.authorUsername
      ? nameLabel(change.authorUsername, change.authorFirstName, change.authorLastName)
      : $localize`:@@direction.history.unknownAuthor:Inconnu`;
  }

  protected delegateOf(change: DelegationChangeResponse): string {
    return nameLabel(change.delegateUsername, change.delegateFirstName, change.delegateLastName);
  }

  protected describe(change: DelegationChangeResponse): string {
    const granted = change.change === DelegationChangeResponseChangeEnum.granted;
    if (change.delegation === DelegationChangeResponseDelegationEnum.directionAdmin) {
      return granted
        ? $localize`:@@direction.history.adminGranted:Désigné administrateur`
        : $localize`:@@direction.history.adminRevoked:Retiré des administrateurs`;
    }
    const application = change.applicationName ?? '';
    return granted
      ? $localize`:@@direction.history.managerGranted:Désigné gestionnaire de ${application}:application:`
      : $localize`:@@direction.history.managerRevoked:Retiré des gestionnaires de ${application}:application:`;
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

  protected createGroup(): void {
    const { name } = this.groupForm.getRawValue();
    this.groupsApi.createGroup(this.direction(), { name }).subscribe(() => {
      this.groupForm.reset();
      this.notify($localize`:@@direction.group.created:Groupe ${name}:name: créé`);
      this.groups.reload();
    });
  }
}
