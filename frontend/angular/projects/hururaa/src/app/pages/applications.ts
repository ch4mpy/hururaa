import { Component, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApplicationsApi, DirectionsApi } from '@api/hururaa-api';
import { PfPageComponent } from 'pf-ui';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { DelegationsService } from '../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../core/resource-events.service';
import { injectNotifier } from './shared/labels';

/**
 * The applications the user has management rights on, with the direction managing each of them.
 * Platform administrators register new ones here (for Keycloak clients that must exist already).
 */
@Component({
  selector: 'app-applications',
  imports: [
    PfPageComponent,
    RouterLink,
    ReactiveFormsModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    SelectModule,
    TableModule,
  ],
  template: `
    <pf-page [withPadding]="true">
      <ng-template #title>
        <span i18n="@@applications.title">Applications</span>
      </ng-template>
      @if (delegations.canManageApplications()) {
        <ng-template #toolbar>
          <p-button
            icon="ri-add-line"
            i18n-label="@@applications.register"
            label="Enregistrer une application"
            (onClick)="creating.set(true)"
          />
        </ng-template>
      }

      <p-table [value]="applications.value() ?? []" [loading]="applications.isLoading()">
        <ng-template #header>
          <tr>
            <th i18n="@@applications.name">Nom</th>
            <th i18n="@@applications.direction">Direction</th>
            <th i18n="@@applications.clients">Clients Keycloak</th>
          </tr>
        </ng-template>
        <ng-template #body let-application>
          <tr>
            <td>
              <a [routerLink]="['/applications', application.id]">{{ application.name }}</a>
            </td>
            <td>
              <a [routerLink]="['/directions', application.direction]">{{
                application.direction.toUpperCase()
              }}</a>
            </td>
            <td>
              <code>{{ application.bffClientId }}</code
              >, <code>{{ application.apiClientId }}</code>
            </td>
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr>
            <td colspan="3" i18n="@@applications.empty">
              Aucune application sur laquelle vous avez des droits de gestion
            </td>
          </tr>
        </ng-template>
      </p-table>
    </pf-page>

    <p-dialog
      [visible]="creating()"
      (visibleChange)="creating.set($event)"
      [modal]="true"
      [style]="{ width: '32rem' }"
      i18n-header="@@applications.register"
      header="Enregistrer une application"
    >
      <form [formGroup]="form" (ngSubmit)="create()" class="flex flex-column gap-3">
        <div class="flex flex-column gap-1">
          <label for="clientPrefix" i18n="@@applications.clientPrefix">Préfixe des clients</label>
          <input pInputText id="clientPrefix" formControlName="clientPrefix" />
          <small i18n="@@applications.clientPrefix.hint"
            >Les clients Keycloak «&nbsp;préfixe-bff&nbsp;» et «&nbsp;préfixe-api&nbsp;» sont créés
            s'ils n'existent pas encore.</small
          >
        </div>
        <div class="flex flex-column gap-1">
          <label for="name" i18n="@@applications.name">Nom</label>
          <input pInputText id="name" formControlName="name" />
        </div>
        <div class="flex flex-column gap-1">
          <label for="direction" i18n="@@applications.direction">Direction</label>
          <p-select
            inputId="direction"
            formControlName="direction"
            [options]="directions.value() ?? []"
            optionLabel="alias"
            optionValue="alias"
            appendTo="body"
          />
        </div>
        <div class="flex justify-content-end gap-2">
          <p-button
            severity="secondary"
            [outlined]="true"
            i18n-label="@@dialog.cancel"
            label="Annuler"
            (onClick)="creating.set(false)"
          />
          <p-button
            type="submit"
            icon="ri-check-line"
            [disabled]="form.invalid"
            i18n-label="@@applications.register.submit"
            label="Enregistrer"
          />
        </div>
      </form>
    </p-dialog>
  `,
})
export class Applications {
  private readonly api = inject(ApplicationsApi);
  private readonly router = inject(Router);
  private readonly notify = injectNotifier();
  protected readonly delegations = inject(DelegationsService);

  /**
   * Only the applications the user has management rights on (the API decides: all of them for a
   * platform administrator, those of the directions they administer, those they manage).
   */
  protected readonly applications = rxResource({
    stream: () => this.api.getApplications(undefined, true),
  });
  private readonly directionsApi = inject(DirectionsApi);
  protected readonly directions = rxResource({ stream: () => this.directionsApi.getDirections() });

  protected readonly creating = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    clientPrefix: ['', [Validators.required, Validators.pattern(/^[a-z][a-z0-9-]*$/)]],
    name: ['', Validators.required],
    direction: ['', Validators.required],
  });

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.APPLICATION)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.applications.reload());
  }

  protected create(): void {
    const request = this.form.getRawValue();
    this.api.createApplication(request, 'response').subscribe((response) => {
      this.creating.set(false);
      this.form.reset();
      this.notify(
        $localize`:@@applications.registered:Application ${request.name}:name: enregistrée`,
      );
      const id = response.headers.get('Location')?.split('/').pop();
      if (id) {
        void this.router.navigate(['/applications', id]);
      } else {
        this.applications.reload();
      }
    });
  }
}
