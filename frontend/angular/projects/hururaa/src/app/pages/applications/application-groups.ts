import { Component, inject, input, numberAttribute } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { GroupsApi } from '@api/hururaa-api';
import { ButtonModule } from 'primeng/button';
import { InputGroupModule } from 'primeng/inputgroup';
import { InputGroupAddonModule } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';
import { injectNotifier } from '../shared/labels';
import { ApplicationContext } from './application-context';
import { GroupRoles } from './group-roles';

/**
 * The groups tab of an application: its groups (named after its client prefix) with the roles
 * they grant, and the creation of new ones, opened right away to grant them roles and members.
 */
@Component({
  selector: 'app-application-groups',
  imports: [
    RouterLink,
    ReactiveFormsModule,
    ButtonModule,
    InputGroupModule,
    InputGroupAddonModule,
    InputTextModule,
    TableModule,
    GroupRoles,
  ],
  template: `
    <form
      [formGroup]="form"
      (ngSubmit)="create()"
      class="flex flex-wrap align-items-center justify-content-between gap-3 mb-3"
    >
      <p class="m-0 text-color-secondary" i18n="@@application.groups.intro">
        Les membres d'un groupe reçoivent les rôles qu'il attribue.
      </p>
      <div class="flex flex-wrap align-items-center gap-2">
        <p-inputgroup class="w-auto">
          <p-inputgroup-addon>
            <code>{{ context.application.value()?.clientPrefix }}.</code>
          </p-inputgroup-addon>
          <input
            pInputText
            formControlName="name"
            i18n-aria-label="@@application.group.name"
            aria-label="Nom du groupe"
            i18n-placeholder="@@application.group.name.placeholder"
            placeholder="agent"
          />
        </p-inputgroup>
        <p-button
          type="submit"
          icon="ri-add-line"
          [disabled]="form.invalid"
          i18n-label="@@application.group.create"
          label="Créer le groupe"
        />
      </div>
    </form>

    <p-table [value]="groups.value() ?? []" [loading]="groups.isLoading()">
      <ng-template #header>
        <tr>
          <th i18n="@@application.group">Groupe</th>
          <th i18n="@@group.roles">Rôles attribués</th>
          <th><span class="sr-only" i18n="@@actions">Actions</span></th>
        </tr>
      </ng-template>
      <ng-template #body let-group>
        <tr>
          <td>
            <a [routerLink]="[group.name]" class="font-semibold">{{ group.name }}</a>
          </td>
          <td><app-group-roles [direction]="direction()" [group]="group.name" /></td>
          <td class="text-right">
            <p-button
              icon="ri-arrow-right-s-line"
              [text]="true"
              [routerLink]="[group.name]"
              i18n-ariaLabel="@@application.group.open"
              ariaLabel="Ouvrir le groupe"
            />
          </td>
        </tr>
      </ng-template>
      <ng-template #emptymessage>
        <tr>
          <td colspan="3" i18n="@@application.groups.empty">Aucun groupe</td>
        </tr>
      </ng-template>
    </p-table>
  `,
})
export class ApplicationGroups {
  private readonly api = inject(GroupsApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notify = injectNotifier();
  protected readonly context = inject(ApplicationContext);

  readonly direction = input.required<string>();
  readonly applicationId = input.required({ transform: numberAttribute });

  protected readonly groups = rxResource({
    params: () => ({ direction: this.direction(), id: this.applicationId() }),
    stream: ({ params }) => this.api.getApplicationGroups(params.direction, params.id),
  });

  /** The group's name within the application, which the API prefixes with its client prefix. */
  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(/^[a-z0-9][a-z0-9._-]*$/)]],
  });

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.GROUP)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => event.tenant === this.direction() && this.groups.reload());
  }

  protected create(): void {
    const name = `${this.context.application.value()?.clientPrefix}.${this.form.getRawValue().name}`;
    this.api
      .createGroup(this.direction(), this.applicationId(), this.form.getRawValue())
      .subscribe(() => {
        this.form.reset();
        this.notify($localize`:@@application.group.created:Groupe ${name}:name: créé`);
        void this.router.navigate([name], { relativeTo: this.route });
      });
  }
}
