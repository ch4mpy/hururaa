import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApplicationRolesApi, GroupsApi, UserResponse } from '@api/hururaa-api';
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
import { PermissionHistory } from './shared/permission-history';
import { UserPicker } from './shared/user-picker';

const PAGE_SIZE = 10;

/**
 * A group of a direction: the roles of its application it grants, and its members. A group belongs
 * to the application whose client prefix starts its name, and is managed by that application's
 * managers and by its direction's administrators.
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
    PermissionHistory,
  ],
  template: `
    <pf-page [withPadding]="true">
      <ng-template #title>{{ group() }}</ng-template>
      @if (canManage()) {
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
        @if (details.value()?.applicationId; as applicationId) {
          <span i18n="@@group.ofApplication">Groupe de l'application</span>&nbsp;
          <a [routerLink]="['/applications', applicationId]">{{
            details.value()?.applicationName
          }}</a>
          ·
        } @else if (details.value()) {
          <span i18n="@@group.withoutApplication"
            >Ce groupe n'appartient à aucune application : il n'attribue aucun rôle par
            Hurura'a.</span
          >
          ·
        }
        <span i18n="@@group.direction">Direction</span>&nbsp;
        <a [routerLink]="['/directions', direction()]">{{ direction().toUpperCase() }}</a>
      </p>

      @if (applicationId()) {
        <h2 i18n="@@group.roles">Rôles attribués</h2>
        <p-table [value]="roles.value() ?? []" [loading]="roles.isLoading()">
          <ng-template #body let-role>
            <tr>
              <td>
                <code>{{ role.role }}</code>
              </td>
              <td class="text-right">
                @if (canManage()) {
                  <p-button
                    icon="ri-close-line"
                    severity="danger"
                    [text]="true"
                    i18n-ariaLabel="@@group.role.remove"
                    ariaLabel="Ne plus attribuer ce rôle"
                    (onClick)="removeRole(role.role)"
                  />
                }
              </td>
            </tr>
          </ng-template>
          <ng-template #emptymessage>
            <tr>
              <td colspan="2" i18n="@@group.roles.empty">Ce groupe n'attribue aucun rôle</td>
            </tr>
          </ng-template>
        </p-table>

        @if (canManage()) {
          <div class="flex flex-wrap align-items-end gap-3 mt-3">
            <div class="flex flex-column gap-1">
              <label for="grantRole" i18n="@@group.role.name">Rôle</label>
              <p-select
                inputId="grantRole"
                [options]="grantableRoles()"
                optionLabel="name"
                optionValue="name"
                [(ngModel)]="grantRole"
              />
            </div>
            <p-button
              icon="ri-add-line"
              [disabled]="!grantRole"
              i18n-label="@@group.role.add"
              label="Attribuer le rôle"
              (onClick)="addRole()"
            />
          </div>
        }
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
            @if (canManage()) {
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
      @if (canManage()) {
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

      <h2 i18n="@@group.history">Historique des permissions</h2>
      <app-permission-history [direction]="direction()" [group]="group()" />
    </pf-page>
  `,
})
export class GroupDetail {
  private readonly groupsApi = inject(GroupsApi);
  private readonly rolesApi = inject(ApplicationRolesApi);
  private readonly confirmation = inject(ConfirmationService);
  private readonly router = inject(Router);
  private readonly notify = injectNotifier();
  private readonly delegations = inject(DelegationsService);
  protected readonly labelOf = userLabel;
  protected readonly pageSize = PAGE_SIZE;

  /** Bound from the `:direction` route parameter. */
  readonly direction = input.required<string>();

  /** Bound from the `:group` route parameter. */
  readonly group = input.required<string>();

  protected readonly details = rxResource({
    params: () => ({ direction: this.direction(), group: this.group() }),
    stream: ({ params }) => this.groupsApi.getGroup(params.direction, params.group),
  });

  /** The application the group belongs to, if any: only its roles can be granted. */
  protected readonly applicationId = computed(() => this.details.value()?.applicationId);

  /** Mirrors the access rule of the API's group roles, members and deletion endpoints. */
  protected readonly canManage = computed(
    () =>
      !!this.details.value() &&
      this.delegations.canManageGroup({
        direction: this.direction(),
        applicationId: this.applicationId(),
      }),
  );

  protected readonly roles = rxResource({
    params: () => ({ direction: this.direction(), group: this.group() }),
    stream: ({ params }) => this.groupsApi.getGroupRoles(params.direction, params.group),
  });

  private readonly applicationRoles = rxResource({
    params: () => (this.canManage() ? this.applicationId() : undefined),
    stream: ({ params }) =>
      params ? this.rolesApi.getApplicationRoles(this.direction(), params) : of([]),
  });

  /** The application's roles the group does not grant yet. */
  protected readonly grantableRoles = computed(() => {
    const granted = new Set((this.roles.value() ?? []).map((r) => r.role));
    return (this.applicationRoles.value() ?? []).filter((r) => !granted.has(r.name));
  });

  protected readonly page = signal(0);

  protected readonly members = rxResource({
    params: () => ({ direction: this.direction(), group: this.group(), page: this.page() }),
    stream: ({ params }) =>
      this.groupsApi.getGroupMembers(params.direction, params.group, params.page, PAGE_SIZE),
  });

  protected grantRole: string | undefined;

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
    const role = this.grantRole;
    if (!role) {
      return;
    }
    this.groupsApi.addGroupRole(this.direction(), this.group(), role).subscribe(() => {
      this.grantRole = undefined;
      this.notify($localize`:@@group.role.added:Le groupe attribue désormais ${role}:role:`);
      this.roles.reload();
    });
  }

  protected removeRole(role: string): void {
    this.groupsApi.removeGroupRole(this.direction(), this.group(), role).subscribe(() => {
      this.notify($localize`:@@group.role.removed:Le groupe n'attribue plus ${role}:role:`);
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
    const applicationId = this.applicationId();
    confirm(this.confirmation, {
      header: $localize`:@@group.delete:Supprimer le groupe`,
      message: $localize`:@@group.delete.confirm:Supprimer le groupe ${group}:group: ? Ses membres perdront les rôles qu'il leur attribue.`,
    }).subscribe(() =>
      this.groupsApi.deleteGroup(this.direction(), group).subscribe(() => {
        this.notify($localize`:@@group.deleted:Groupe ${group}:group: supprimé`);
        void this.router.navigate(
          applicationId ? ['/applications', applicationId] : ['/directions', this.direction()],
        );
      }),
    );
  }
}
