import { Component, inject, input, numberAttribute } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApplicationRolesApi } from '@api/hururaa-api';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { confirm } from '../../core/confirm';
import { injectNotifier } from '../shared/labels';
import { ApplicationContext } from './application-context';

/** The roles tab of an application: the client roles of its API, which its groups grant. */
@Component({
  selector: 'app-application-roles',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, TableModule],
  template: `
    <form
      [formGroup]="form"
      (ngSubmit)="create()"
      class="flex flex-wrap align-items-end gap-3 mb-3"
    >
      <div class="flex flex-column gap-1">
        <label for="roleName" i18n="@@application.role.name">Nom</label>
        <input
          pInputText
          id="roleName"
          formControlName="name"
          [placeholder]="(context.application.value()?.clientPrefix ?? '') + '.ressource.action'"
        />
      </div>
      <div class="flex flex-column gap-1 flex-grow-1">
        <label for="roleDescription" i18n="@@application.role.description">Description</label>
        <input pInputText id="roleDescription" formControlName="description" />
      </div>
      <p-button
        type="submit"
        icon="ri-add-line"
        [disabled]="form.invalid"
        i18n-label="@@application.role.create"
        label="Définir le rôle"
      />
    </form>

    <p-table [value]="context.roles.value() ?? []" [loading]="context.roles.isLoading()">
      <ng-template #header>
        <tr>
          <th i18n="@@application.roles.name">Nom</th>
          <th i18n="@@application.roles.description">Description</th>
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
              (onClick)="delete(role.name)"
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
  `,
})
export class ApplicationRoles {
  private readonly api = inject(ApplicationRolesApi);
  private readonly confirmation = inject(ConfirmationService);
  private readonly notify = injectNotifier();
  protected readonly context = inject(ApplicationContext);

  readonly direction = input.required<string>();
  readonly applicationId = input.required({ transform: numberAttribute });

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(/^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)*$/)]],
    description: [''],
  });

  protected create(): void {
    const { name, description } = this.form.getRawValue();
    this.api
      .createApplicationRole(this.direction(), this.applicationId(), {
        name,
        description: description || undefined,
      })
      .subscribe(() => {
        this.form.reset();
        this.notify($localize`:@@application.role.created:Rôle ${name}:name: défini`);
        this.context.roles.reload();
      });
  }

  protected delete(role: string): void {
    confirm(this.confirmation, {
      header: $localize`:@@application.role.delete:Supprimer le rôle`,
      message: $localize`:@@application.role.delete.confirm:Supprimer le rôle ${role}:role: ? Il sera retiré de tous les groupes qui l'attribuent.`,
    }).subscribe(() =>
      this.api.deleteApplicationRole(this.direction(), this.applicationId(), role).subscribe(() => {
        this.notify($localize`:@@application.role.deleted:Rôle ${role}:role: supprimé`);
        this.context.roles.reload();
      }),
    );
  }
}
