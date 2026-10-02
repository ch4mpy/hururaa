import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  ApplicationRolesApi,
  ApplicationsApi,
  GroupRoleResponse,
  GroupsApi,
  UserResponse,
} from '@api/hururaa-api';
import { PfPageComponent } from 'pf-ui';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { of } from 'rxjs';
import { confirm } from '../core/confirm';
import { DelegationsService } from '../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../core/resource-events.service';
import { injectNotifier, userLabel } from './shared/labels';
import { UserPicker } from './shared/user-picker';

const PAGE_SIZE = 10;

/**
 * A group of a direction: the roles it grants (of the direction's applications only, each managed
 * by that application's managers) and its members.
 *
 * Changing the members or deleting the group requires managing every application whose roles the
 * group grants: the API enforces it, a refusal shows in the error banner.
 */
@Component({
  selector: 'app-group-detail',
  imports: [
    PfPageComponent,
    RouterLink,
    FormsModule,
    ButtonModule,
    SelectModule,
    TableModule,
    UserPicker,
  ],
  template: `
    <pf-page [withPadding]="true">
      <ng-template #title>{{ group() }}</ng-template>
      @if (canManageMembers()) {
        <ng-template #toolbar>
          <p-button
            severity="danger"
            [outlined]="true"
            icon="ri-delete-bin-line"
            i18n-label="@@group.delete"
            label="Supprimer le groupe"
            (onClick)="deleteGroup()"
          />
        </ng-template>
      }
      <p class="mt-0">
        <span i18n="@@group.of">Groupe de la direction</span>&nbsp;
        <a [routerLink]="['/directions', direction()]">{{ direction().toUpperCase() }}</a>
      </p>

      <h2 i18n="@@group.roles">Rôles attribués</h2>
      <p-table [value]="roles.value() ?? []" [loading]="roles.isLoading()">
        <ng-template #header>
          <tr>
            <th i18n="@@group.role.application">Application</th>
            <th i18n="@@group.role.name">Rôle</th>
            <th><span class="sr-only" i18n="@@actions">Actions</span></th>
          </tr>
        </ng-template>
        <ng-template #body let-role>
          <tr>
            <td>
              <a [routerLink]="['/applications', role.applicationId]">{{ role.applicationName }}</a>
            </td>
            <td>
              <code>{{ role.role }}</code>
            </td>
            <td class="text-right">
              @if (
                delegations.canGrantRolesOf({ id: role.applicationId, direction: direction() })
              ) {
                <p-button
                  icon="ri-close-line"
                  severity="danger"
                  [text]="true"
                  i18n-ariaLabel="@@group.role.remove"
                  ariaLabel="Ne plus attribuer ce rôle"
                  (onClick)="removeRole(role)"
                />
              }
            </td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr>
            <td colspan="3" i18n="@@group.roles.empty">Ce groupe n'attribue aucun rôle</td>
          </tr>
        </ng-template>
      </p-table>

      @if (grantableApplications().length) {
        <div class="flex flex-wrap align-items-end gap-3 mt-3">
          <div class="flex flex-column gap-1">
            <label for="grantApplication" i18n="@@group.role.application">Application</label>
            <p-select
              inputId="grantApplication"
              [options]="grantableApplications()"
              optionLabel="name"
              optionValue="id"
              [ngModel]="grantApplicationId()"
              (ngModelChange)="grantApplicationId.set($event)"
            />
          </div>
          <div class="flex flex-column gap-1">
            <label for="grantRole" i18n="@@group.role.name">Rôle</label>
            <p-select
              inputId="grantRole"
              [options]="grantableRoles.value() ?? []"
              optionLabel="name"
              optionValue="name"
              [(ngModel)]="grantRole"
            />
          </div>
          <p-button
            icon="ri-add-line"
            [disabled]="!grantApplicationId() || !grantRole"
            i18n-label="@@group.role.add"
            label="Attribuer le rôle"
            (onClick)="addRole()"
          />
        </div>
      }

      <h2 i18n="@@group.members">Membres</h2>
      <p-table
        [value]="members.value()?.content ?? []"
        [lazy]="true"
        [paginator]="true"
        [rows]="pageSize"
        [totalRecords]="members.value()?.page?.totalElements ?? 0"
        [loading]="members.isLoading()"
        (onLazyLoad)="page.set(pageOf($event))"
      >
        <ng-template #body let-member>
          <tr>
            <td>{{ labelOf(member) }}</td>
            <td>{{ member.email }}</td>
            @if (canManageMembers()) {
              <td class="text-right">
                <p-button
                  icon="ri-user-unfollow-line"
                  severity="danger"
                  [text]="true"
                  i18n-ariaLabel="@@group.member.remove"
                  ariaLabel="Retirer du groupe"
                  (onClick)="removeMember(member)"
                />
              </td>
            }
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr>
            <td colspan="3" i18n="@@group.members.empty">Aucun membre</td>
          </tr>
        </ng-template>
      </p-table>
      @if (canManageMembers()) {
        <div class="mt-3">
          <app-user-picker
            [direction]="direction()"
            inputId="member-picker"
            i18n-label="@@group.member.add"
            label="Ajouter au groupe"
            (picked)="addMember($event)"
          />
        </div>
      }
    </pf-page>
  `,
})
export class GroupDetail {
  private readonly groupsApi = inject(GroupsApi);
  private readonly rolesApi = inject(ApplicationRolesApi);
  private readonly confirmation = inject(ConfirmationService);
  private readonly router = inject(Router);
  private readonly notify = injectNotifier();
  protected readonly delegations = inject(DelegationsService);
  protected readonly labelOf = userLabel;
  protected readonly pageSize = PAGE_SIZE;

  /** Bound from the `:direction` route parameter. */
  readonly direction = input.required<string>();

  /** Bound from the `:group` route parameter. */
  readonly group = input.required<string>();

  /**
   * Whether to offer changing the group's members, or deleting it: for a manager who is not an
   * administrator, the API also requires managing every application whose roles the group grants.
   */
  protected readonly canManageMembers = computed(() =>
    this.delegations.canCreateGroupsIn(this.direction()),
  );

  private readonly applicationsApi = inject(ApplicationsApi);

  private readonly directionApplications = rxResource({
    params: () => this.direction(),
    stream: ({ params }) => this.applicationsApi.getApplications(params),
  });

  /** The applications of this direction whose roles the user may grant. */
  protected readonly grantableApplications = computed(() =>
    (this.directionApplications.value() ?? []).filter((a) => this.delegations.canGrantRolesOf(a)),
  );

  protected readonly roles = rxResource({
    params: () => ({ direction: this.direction(), group: this.group() }),
    stream: ({ params }) => this.groupsApi.getGroupRoles(params.direction, params.group),
  });

  protected readonly page = signal(0);

  protected readonly members = rxResource({
    params: () => ({ direction: this.direction(), group: this.group(), page: this.page() }),
    stream: ({ params }) =>
      this.groupsApi.getGroupMembers(params.direction, params.group, params.page, PAGE_SIZE),
  });

  protected readonly grantApplicationId = signal<number | undefined>(undefined);
  protected grantRole: string | undefined;

  protected readonly grantableRoles = rxResource({
    params: () => this.grantApplicationId(),
    stream: ({ params }) =>
      params ? this.rolesApi.getApplicationRoles(this.direction(), params) : of([]),
  });

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.GROUP)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => {
        if (event.tenant === this.direction() && event.resourceId === this.group()) {
          this.roles.reload();
          this.members.reload();
        }
      });
  }

  protected pageOf(event: TableLazyLoadEvent): number {
    return Math.floor((event.first ?? 0) / PAGE_SIZE);
  }

  protected addRole(): void {
    const applicationId = this.grantApplicationId();
    const role = this.grantRole;
    if (!applicationId || !role) {
      return;
    }
    this.groupsApi
      .addGroupRole(this.direction(), this.group(), applicationId, role)
      .subscribe(() => {
        this.grantRole = undefined;
        this.notify($localize`:@@group.role.added:Le groupe attribue désormais ${role}:role:`);
        this.roles.reload();
      });
  }

  protected removeRole(role: GroupRoleResponse): void {
    this.groupsApi
      .removeGroupRole(this.direction(), this.group(), role.applicationId, role.role)
      .subscribe(() => {
        this.notify($localize`:@@group.role.removed:Le groupe n'attribue plus ${role.role}:role:`);
        this.roles.reload();
      });
  }

  protected addMember(user: UserResponse): void {
    this.groupsApi.addGroupMember(this.direction(), this.group(), user.id).subscribe(() => {
      this.notify($localize`:@@group.member.added:${userLabel(user)}:user: ajouté au groupe`);
      this.members.reload();
    });
  }

  protected removeMember(user: UserResponse): void {
    this.groupsApi.removeGroupMember(this.direction(), this.group(), user.id).subscribe(() => {
      this.notify($localize`:@@group.member.removed:${userLabel(user)}:user: retiré du groupe`);
      this.members.reload();
    });
  }

  protected deleteGroup(): void {
    const group = this.group();
    confirm(this.confirmation, {
      header: $localize`:@@group.delete:Supprimer le groupe`,
      message: $localize`:@@group.delete.confirm:Supprimer le groupe ${group}:group: ? Ses membres perdront les rôles qu'il leur attribue.`,
    }).subscribe(() =>
      this.groupsApi.deleteGroup(this.direction(), group).subscribe(() => {
        this.notify($localize`:@@group.deleted:Groupe ${group}:group: supprimé`);
        void this.router.navigate(['/directions', this.direction()]);
      }),
    );
  }
}
