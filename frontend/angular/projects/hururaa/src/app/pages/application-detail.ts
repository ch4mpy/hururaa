import { Component, computed, effect, inject, input, numberAttribute } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  ApplicationManagersApi,
  ApplicationRolesApi,
  ApplicationsApi,
  GroupsApi,
  UserResponse,
} from '@api/hururaa-api';
import { PfPageComponent } from 'pf-ui';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { of } from 'rxjs';
import { confirm } from '../core/confirm';
import { DelegationsService } from '../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../core/resource-events.service';
import { injectNotifier, userLabel } from './shared/labels';
import { PermissionHistory } from './shared/permission-history';
import { UserPicker } from './shared/user-picker';

/**
 * An application of a direction (for good: it never changes direction): its name (edited by its
 * direction's administrators), its roles, its groups (named after its client prefix) and its
 * managers (defined by its direction's administrators and by its managers).
 */
@Component({
  selector: 'app-application-detail',
  imports: [
    PfPageComponent,
    RouterLink,
    ReactiveFormsModule,
    ButtonModule,
    InputTextModule,
    TableModule,
    UserPicker,
    PermissionHistory,
  ],
  template: `
    <pf-page [withPadding]="true">
      <ng-template #title>{{ application.value()?.name }}</ng-template>

      @if (application.value(); as app) {
        <p class="mt-0">
          <span i18n="@@application.managedBy">Gérée par la direction</span>&nbsp;
          <a [routerLink]="['/directions', app.direction]">{{ app.direction.toUpperCase() }}</a>
          · <code>{{ app.bffClientId }}</code> · <code>{{ app.apiClientId }}</code>
        </p>

        @if (canEdit()) {
          <h2 i18n="@@application.identity">Identité</h2>
          <form [formGroup]="form" (ngSubmit)="save()" class="flex flex-wrap align-items-end gap-3">
            <div class="flex flex-column gap-1">
              <label for="name" i18n="@@applications.name">Nom</label>
              <input pInputText id="name" formControlName="name" />
            </div>
            <p-button
              type="submit"
              icon="ri-save-line"
              [disabled]="form.invalid || form.pristine"
              i18n-label="@@save"
              label="Enregistrer"
            />
            <p-button
              severity="danger"
              [outlined]="true"
              icon="ri-delete-bin-line"
              i18n-label="@@application.unregister"
              label="Désenregistrer"
              (onClick)="unregister()"
            />
          </form>
          <small class="block mt-2" i18n="@@application.unregister.groupsHint"
            >Une application ne peut être désenregistrée tant qu'elle a des groupes.</small
          >
        }

        @if (canManage()) {
          <h2 i18n="@@application.roles">Rôles</h2>
          <p-table [value]="roles.value() ?? []" [loading]="roles.isLoading()">
            <ng-template #header>
              <tr>
                <th i18n="@@application.role.name">Nom</th>
                <th i18n="@@application.role.description">Description</th>
                <th><span class="sr-only" i18n="@@actions">Actions</span></th>
              </tr>
            </ng-template>
            <ng-template #body let-role>
              <tr>
                <td>
                  <code>{{ role.name }}</code>
                </td>
                <td>{{ role.description }}</td>
                <td class="text-right">
                  <p-button
                    icon="ri-delete-bin-line"
                    severity="danger"
                    [text]="true"
                    i18n-ariaLabel="@@application.role.delete"
                    ariaLabel="Supprimer le rôle"
                    (onClick)="deleteRole(role.name)"
                  />
                </td>
              </tr>
            </ng-template>
            <ng-template #emptymessage>
              <tr>
                <td colspan="3" i18n="@@application.roles.empty">Aucun rôle défini</td>
              </tr>
            </ng-template>
          </p-table>

          <form
            [formGroup]="roleForm"
            (ngSubmit)="createRole()"
            class="flex flex-wrap align-items-end gap-3 mt-3"
          >
            <div class="flex flex-column gap-1">
              <label for="roleName" i18n="@@application.role.name">Nom</label>
              <input
                pInputText
                id="roleName"
                formControlName="name"
                [placeholder]="app.clientPrefix + '.ressource.action'"
              />
            </div>
            <div class="flex flex-column gap-1 flex-grow-1">
              <label for="roleDescription" i18n="@@application.role.description">Description</label>
              <input pInputText id="roleDescription" formControlName="description" />
            </div>
            <p-button
              type="submit"
              icon="ri-add-line"
              [disabled]="roleForm.invalid"
              i18n-label="@@application.role.create"
              label="Définir le rôle"
            />
          </form>

          <h2 i18n="@@application.groups">Groupes</h2>
          <ul>
            @for (group of groups.value() ?? []; track group.id) {
              <li>
                <a [routerLink]="['/directions', app.direction, 'groups', group.name]">{{
                  group.name
                }}</a>
              </li>
            } @empty {
              <li i18n="@@application.groups.empty">Aucun groupe</li>
            }
          </ul>
          <form
            [formGroup]="groupForm"
            (ngSubmit)="createGroup()"
            class="flex flex-wrap align-items-end gap-3"
          >
            <div class="flex flex-column gap-1">
              <label for="groupName" i18n="@@application.group.name">Nom du groupe</label>
              <div class="flex align-items-center gap-1">
                <code>{{ app.clientPrefix }}.</code>
                <input
                  pInputText
                  id="groupName"
                  formControlName="name"
                  i18n-placeholder="@@application.group.name.placeholder"
                  placeholder="agent"
                />
              </div>
            </div>
            <p-button
              type="submit"
              icon="ri-add-line"
              [disabled]="groupForm.invalid"
              i18n-label="@@application.group.create"
              label="Créer le groupe"
            />
          </form>

          <h2 i18n="@@application.managers">Gestionnaires</h2>
          <p-table [value]="managers.value() ?? []" [loading]="managers.isLoading()">
            <ng-template #body let-manager>
              <tr>
                <td>{{ labelOf(manager) }}</td>
                <td>{{ manager.email }}</td>
                <td class="text-right">
                  <p-button
                    icon="ri-user-unfollow-line"
                    severity="danger"
                    [text]="true"
                    i18n-ariaLabel="@@application.manager.remove"
                    ariaLabel="Retirer ce gestionnaire"
                    (onClick)="removeManager(manager)"
                  />
                </td>
              </tr>
            </ng-template>
            <ng-template #emptymessage>
              <tr>
                <td colspan="3" i18n="@@application.managers.empty">Aucun gestionnaire</td>
              </tr>
            </ng-template>
          </p-table>
          <div class="mt-3">
            <app-user-picker
              [direction]="app.direction"
              inputId="manager-picker"
              i18n-label="@@application.manager.add"
              label="Désigner gestionnaire"
              (picked)="addManager($event)"
            />
          </div>

          <h2 i18n="@@application.history">Historique des permissions</h2>
          <app-permission-history [direction]="app.direction" [applicationId]="app.id" />
        } @else {
          <p i18n="@@application.noSay">
            Les rôles et les gestionnaires de cette application ne sont visibles que de ses
            gestionnaires et des administrateurs de sa direction.
          </p>
        }
      }
    </pf-page>
  `,
})
export class ApplicationDetail {
  private readonly applicationsApi = inject(ApplicationsApi);
  private readonly rolesApi = inject(ApplicationRolesApi);
  private readonly managersApi = inject(ApplicationManagersApi);
  private readonly groupsApi = inject(GroupsApi);
  private readonly confirmation = inject(ConfirmationService);
  private readonly router = inject(Router);
  private readonly notify = injectNotifier();
  protected readonly delegations = inject(DelegationsService);
  protected readonly labelOf = userLabel;

  /** Bound from the `:applicationId` route parameter. */
  readonly applicationId = input.required({ transform: numberAttribute });

  protected readonly application = rxResource({
    params: () => this.applicationId(),
    stream: ({ params }) => this.applicationsApi.getApplication(params),
  });

  /** The direction managing the application: its roles and managers are addressed under it. */
  private readonly direction = computed(() => this.application.value()?.direction);

  /** Mirrors the access rule of the API's application update and delete endpoints. */
  protected readonly canEdit = computed(() => {
    const direction = this.direction();
    return !!direction && this.delegations.canEditApplicationsOf(direction);
  });

  /** Mirrors the access rule of the API's application roles and managers endpoints. */
  protected readonly canManage = computed(() => {
    const direction = this.direction();
    return (
      !!direction && this.delegations.canManageApplication({ id: this.applicationId(), direction })
    );
  });

  private readonly directionApplication = computed(() => {
    const direction = this.direction();
    return direction && this.canManage() ? { direction, id: this.applicationId() } : undefined;
  });

  protected readonly roles = rxResource({
    params: () => this.directionApplication(),
    stream: ({ params }) =>
      params ? this.rolesApi.getApplicationRoles(params.direction, params.id) : of([]),
  });

  protected readonly managers = rxResource({
    params: () => this.directionApplication(),
    stream: ({ params }) =>
      params ? this.managersApi.getApplicationManagers(params.direction, params.id) : of([]),
  });

  protected readonly groups = rxResource({
    params: () => this.directionApplication(),
    stream: ({ params }) =>
      params ? this.groupsApi.getApplicationGroups(params.direction, params.id) : of([]),
  });

  private readonly formBuilder = inject(FormBuilder).nonNullable;

  protected readonly form = this.formBuilder.group({
    name: ['', Validators.required],
  });

  protected readonly roleForm = this.formBuilder.group({
    name: ['', [Validators.required, Validators.pattern(/^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)*$/)]],
    description: [''],
  });

  /** The group's name within the application, which the API prefixes with its client prefix. */
  protected readonly groupForm = this.formBuilder.group({
    name: ['', [Validators.required, Validators.pattern(/^[a-z0-9][a-z0-9._-]*$/)]],
  });

  constructor() {
    effect(() => {
      const app = this.application.value();
      if (app) {
        this.form.reset({ name: app.name });
      }
    });
    const events = inject(ResourceEventsService);
    events
      .of(ResourceTypes.APPLICATION)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => {
        if (event.resourceId === String(this.applicationId())) {
          this.application.reload();
          this.roles.reload();
          this.managers.reload();
        }
      });
    events
      .of(ResourceTypes.GROUP)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => event.tenant === this.direction() && this.groups.reload());
  }

  protected createGroup(): void {
    const name = `${this.application.value()?.clientPrefix}.${this.groupForm.getRawValue().name}`;
    this.groupsApi
      .createGroup(this.loadedDirection(), this.applicationId(), this.groupForm.getRawValue())
      .subscribe(() => {
        this.groupForm.reset();
        this.notify($localize`:@@application.group.created:Groupe ${name}:name: créé`);
        this.groups.reload();
      });
  }

  protected save(): void {
    const { name } = this.form.getRawValue();
    this.applicationsApi
      .updateApplication(this.loadedDirection(), this.applicationId(), { name })
      .subscribe(() => {
        this.notify($localize`:@@application.saved:Application ${name}:name: mise à jour`);
        this.application.reload();
        this.delegations.refresh();
      });
  }

  protected unregister(): void {
    const name = this.application.value()?.name ?? '';
    confirm(this.confirmation, {
      header: $localize`:@@application.unregister:Désenregistrer`,
      message: $localize`:@@application.unregister.confirm:Désenregistrer l'application ${name}:name: ? Ses clients Keycloak ne sont pas supprimés.`,
    }).subscribe(() =>
      this.applicationsApi
        .deleteApplication(this.loadedDirection(), this.applicationId())
        .subscribe(() => {
          this.notify(
            $localize`:@@application.unregistered:Application ${name}:name: désenregistrée`,
          );
          this.delegations.refresh();
          void this.router.navigate(['/applications']);
        }),
    );
  }

  protected createRole(): void {
    const { name, description } = this.roleForm.getRawValue();
    this.rolesApi
      .createApplicationRole(this.loadedDirection(), this.applicationId(), {
        name,
        description: description || undefined,
      })
      .subscribe(() => {
        this.roleForm.reset();
        this.notify($localize`:@@application.role.created:Rôle ${name}:name: défini`);
        this.roles.reload();
      });
  }

  protected deleteRole(role: string): void {
    confirm(this.confirmation, {
      header: $localize`:@@application.role.delete:Supprimer le rôle`,
      message: $localize`:@@application.role.delete.confirm:Supprimer le rôle ${role}:role: ? Il sera retiré de tous les groupes qui l'attribuent.`,
    }).subscribe(() =>
      this.rolesApi
        .deleteApplicationRole(this.loadedDirection(), this.applicationId(), role)
        .subscribe(() => {
          this.notify($localize`:@@application.role.deleted:Rôle ${role}:role: supprimé`);
          this.roles.reload();
        }),
    );
  }

  protected addManager(user: UserResponse): void {
    this.managersApi
      .addApplicationManager(this.loadedDirection(), this.applicationId(), user.id)
      .subscribe(() => {
        this.notify(
          $localize`:@@application.manager.added:${userLabel(user)}:user: désigné gestionnaire`,
        );
        this.managers.reload();
        this.delegations.refresh();
      });
  }

  protected removeManager(user: UserResponse): void {
    this.managersApi
      .removeApplicationManager(this.loadedDirection(), this.applicationId(), user.id)
      .subscribe(() => {
        this.notify(
          $localize`:@@application.manager.removed:${userLabel(user)}:user: n'est plus gestionnaire`,
        );
        this.managers.reload();
        this.delegations.refresh();
      });
  }

  /** The roles and managers actions are only offered once the application is loaded. */
  private loadedDirection(): string {
    const direction = this.direction();
    if (!direction) {
      throw new Error('The application is not loaded yet');
    }
    return direction;
  }
}
