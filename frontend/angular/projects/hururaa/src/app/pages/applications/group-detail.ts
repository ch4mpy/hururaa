import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { GroupsApi, UserResponse } from '@api/hururaa-api';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { confirm } from '../../core/confirm';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';
import { injectNotifier, userLabel } from '../shared/labels';
import { PermissionHistory } from '../shared/permission-history';
import { UserPicker } from '../shared/user-picker';
import { ApplicationContext } from './application-context';

const PAGE_SIZE = 10;

/**
 * A group of an application, in its groups tab: the application's roles it grants and its
 * members (who are granted those roles in the direction), managed by the application's managers
 * and the direction's administrators (who alone see the tab).
 */
@Component({
  selector: 'app-group-detail',
  imports: [
    RouterLink,
    FormsModule,
    ButtonModule,
    SelectModule,
    TableModule,
    UserPicker,
    PermissionHistory,
  ],
  template: `
    <a
      [routerLink]="['/applications', direction(), applicationId(), 'groups']"
      class="inline-flex align-items-center gap-1 mb-3"
    >
      <i class="ri-arrow-left-line" aria-hidden="true"></i>
      <span i18n="@@group.back">Tous les groupes</span>
    </a>

    @if (details.value(); as details) {
      @if (details.applicationId !== applicationId()) {
        <p i18n="@@group.otherApplication">Ce groupe n'appartient pas à cette application.</p>
      } @else {
        <div class="flex flex-wrap align-items-center justify-content-between gap-3 mb-4">
          <h3 class="text-xl m-0">
            <i class="ri-group-line mr-2" aria-hidden="true"></i>{{ group() }}
          </h3>
          <p-button
            severity="danger"
            [outlined]="true"
            icon="ri-delete-bin-line"
            i18n-label="@@group.delete"
            label="Supprimer le groupe"
            (onClick)="deleteGroup()"
          />
        </div>

        <section class="mb-5">
          <h4 class="text-base font-semibold mt-0 mb-2" i18n="@@group.roles">Rôles attribués</h4>
          <div class="flex flex-wrap align-items-center gap-2">
            @for (role of roles.value() ?? []; track role.role) {
              <span
                class="inline-flex align-items-center gap-1 pl-2 border-1 surface-border border-round"
              >
                <code>{{ role.role }}</code>
                <p-button
                  icon="ri-close-line"
                  severity="secondary"
                  [text]="true"
                  size="small"
                  i18n-ariaLabel="@@group.role.remove"
                  ariaLabel="Ne plus attribuer ce rôle"
                  (onClick)="removeRole(role.role)"
                />
              </span>
            } @empty {
              @if (!roles.isLoading()) {
                <span class="text-color-secondary" i18n="@@group.roles.empty"
                  >Ce groupe n'attribue aucun rôle</span
                >
              }
            }
          </div>
          @if (grantableRoles().length) {
            <div class="flex flex-wrap align-items-center gap-2 mt-3">
              <p-select
                [options]="grantableRoles()"
                optionLabel="name"
                optionValue="name"
                [(ngModel)]="grantRole"
                i18n-placeholder="@@group.role.pick"
                placeholder="Rôle à attribuer"
                i18n-ariaLabel="@@group.role.pick"
                ariaLabel="Rôle à attribuer"
              >
                <ng-template #item let-role>
                  <div class="flex flex-column">
                    <code>{{ role.name }}</code>
                    @if (role.description) {
                      <small class="text-color-secondary">{{ role.description }}</small>
                    }
                  </div>
                </ng-template>
              </p-select>
              <p-button
                icon="ri-add-line"
                [disabled]="!grantRole"
                i18n-label="@@group.role.add"
                label="Attribuer le rôle"
                (onClick)="addRole()"
              />
            </div>
          } @else if (!context.roles.isLoading() && !context.roles.value()?.length) {
            <p class="mt-3 mb-0 text-color-secondary">
              <span i18n="@@group.roles.noneDefined">L'application n'a aucun rôle :</span>&nbsp;
              <a
                [routerLink]="['/applications', direction(), applicationId(), 'roles']"
                i18n="@@group.roles.define"
                >définir ses rôles</a
              >
            </p>
          }
        </section>

        <section class="mb-5">
          <div class="flex flex-wrap align-items-center justify-content-between gap-3 mb-2">
            <h4 class="text-base font-semibold m-0" i18n="@@group.members">Membres</h4>
            <app-user-picker
              [direction]="direction()"
              inputId="member-picker"
              i18n-label="@@group.member.add"
              label="Ajouter au groupe"
              (picked)="addMember($event)"
            />
          </div>
          <p-table
            [value]="members.value()?.content ?? []"
            [lazy]="true"
            [paginator]="true"
            [rows]="pageSize"
            [totalRecords]="members.value()?.page?.totalElements ?? 0"
            [loading]="members.isLoading()"
            (onLazyLoad)="page.set(pageOf($event))"
          >
            <ng-template #header>
              <tr>
                <th i18n="@@user.name">Nom</th>
                <th i18n="@@user.email">Courriel</th>
                <th><span class="sr-only" i18n="@@actions">Actions</span></th>
              </tr>
            </ng-template>
            <ng-template #body let-member>
              <tr>
                <td>{{ labelOf(member) }}</td>
                <td>{{ member.email }}</td>
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
              </tr>
            </ng-template>
            <ng-template #emptymessage>
              <tr>
                <td colspan="3" i18n="@@group.members.empty">Aucun membre</td>
              </tr>
            </ng-template>
          </p-table>
        </section>

        <!-- folded: loaded only once unfolded -->
        <details class="p-3 border-1 surface-border border-round" (toggle)="toggleHistory($event)">
          <summary class="cursor-pointer font-semibold" i18n="@@group.history">
            Historique des permissions
          </summary>
          @if (historyOpen()) {
            <div class="mt-3">
              <app-permission-history [direction]="direction()" [group]="group()" />
            </div>
          }
        </details>
      }
    }
  `,
})
export class GroupDetail {
  private readonly api = inject(GroupsApi);
  private readonly confirmation = inject(ConfirmationService);
  private readonly router = inject(Router);
  private readonly notify = injectNotifier();
  protected readonly context = inject(ApplicationContext);
  protected readonly labelOf = userLabel;
  protected readonly pageSize = PAGE_SIZE;

  readonly direction = input.required<string>();
  readonly applicationId = input.required({ transform: numberAttribute });

  /** Bound from the `:group` route parameter. */
  readonly group = input.required<string>();

  private readonly params = computed(() => ({ direction: this.direction(), group: this.group() }));

  protected readonly details = rxResource({
    params: this.params,
    stream: ({ params }) => this.api.getGroup(params.direction, params.group),
  });

  protected readonly roles = rxResource({
    params: this.params,
    stream: ({ params }) => this.api.getGroupRoles(params.direction, params.group),
  });

  /** The application's roles the group does not grant yet. */
  protected readonly grantableRoles = computed(() => {
    const granted = new Set((this.roles.value() ?? []).map((r) => r.role));
    return (this.context.roles.value() ?? []).filter((r) => !granted.has(r.name));
  });

  protected readonly page = signal(0);

  protected readonly members = rxResource({
    params: () => ({ ...this.params(), page: this.page() }),
    stream: ({ params }) =>
      this.api.getGroupMembers(params.direction, params.group, params.page, PAGE_SIZE),
  });

  protected grantRole: string | undefined;

  protected readonly historyOpen = signal(false);

  protected toggleHistory(event: Event): void {
    this.historyOpen.set((event.target as HTMLDetailsElement).open);
  }

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
    this.api.addGroupRole(this.direction(), this.group(), role).subscribe(() => {
      this.grantRole = undefined;
      this.notify($localize`:@@group.role.added:Le groupe attribue désormais ${role}:role:`);
      this.roles.reload();
    });
  }

  protected removeRole(role: string): void {
    this.api.removeGroupRole(this.direction(), this.group(), role).subscribe(() => {
      this.notify($localize`:@@group.role.removed:Le groupe n'attribue plus ${role}:role:`);
      this.roles.reload();
    });
  }

  protected addMember(user: UserResponse): void {
    this.api.addGroupMember(this.direction(), this.group(), user.id).subscribe(() => {
      this.notify($localize`:@@group.member.added:${userLabel(user)}:user: ajouté au groupe`);
      this.members.reload();
    });
  }

  protected removeMember(user: UserResponse): void {
    this.api.removeGroupMember(this.direction(), this.group(), user.id).subscribe(() => {
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
      this.api.deleteGroup(this.direction(), group).subscribe(() => {
        this.notify($localize`:@@group.deleted:Groupe ${group}:group: supprimé`);
        void this.router.navigate([
          '/applications',
          this.direction(),
          this.applicationId(),
          'groups',
        ]);
      }),
    );
  }
}
