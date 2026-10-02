import { Component, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DirectionsApi } from '@api/hururaa-api';
import { PfPageComponent } from 'pf-ui';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { DelegationsService } from '../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../core/resource-events.service';
import { UserService } from '../core/user.service';
import { injectNotifier } from './shared/labels';

/**
 * The directions (Keycloak organizations), with the current user's role in each of them. Hurura'a
 * administrators create new ones here.
 */
@Component({
  selector: 'app-directions',
  imports: [
    PfPageComponent,
    RouterLink,
    ReactiveFormsModule,
    ButtonModule,
    CardModule,
    DialogModule,
    InputTextModule,
    TagModule,
  ],
  template: `
    <pf-page [withPadding]="true">
      <ng-template #title>
        <span i18n="@@directions.title">Directions</span>
      </ng-template>
      @if (delegations.isAdmin()) {
        <ng-template #toolbar>
          <p-button
            icon="ri-add-line"
            i18n-label="@@directions.create"
            label="Créer une direction"
            (onClick)="creating.set(true)"
          />
        </ng-template>
      }
      <div class="grid">
        @for (direction of directions.value() ?? []; track direction.alias) {
          <div class="col-12 md:col-6 lg:col-4">
            <p-card>
              <ng-template #title>
                <a [routerLink]="['/directions', direction.alias]">{{
                  direction.alias.toUpperCase()
                }}</a>
              </ng-template>
              <p class="mt-0">{{ direction.description }}</p>
              <div class="flex flex-wrap gap-2">
                @if (user.directions().includes(direction.alias)) {
                  <p-tag severity="info" i18n-value="@@directions.member" value="Membre" />
                }
                @if (delegations.isDirectionAdmin(direction.alias)) {
                  <p-tag severity="warn" i18n-value="@@directions.admin" value="Administrateur" />
                }
                @if (delegations.isManagerInDirection(direction.alias)) {
                  <p-tag
                    severity="success"
                    i18n-value="@@directions.manager"
                    value="Gestionnaire"
                  />
                }
                @if (direction.alias === delegations.current().platformOrganization) {
                  <p-tag
                    severity="danger"
                    i18n-value="@@directions.runsHururaa"
                    value="Exploite Hurura'a"
                  />
                }
              </div>
            </p-card>
          </div>
        }
      </div>
    </pf-page>

    <p-dialog
      [visible]="creating()"
      (visibleChange)="creating.set($event)"
      [modal]="true"
      [style]="{ width: '32rem' }"
      i18n-header="@@directions.create"
      header="Créer une direction"
    >
      <form [formGroup]="form" (ngSubmit)="create()" class="flex flex-column gap-3">
        <div class="flex flex-column gap-1">
          <label for="alias" i18n="@@directions.alias">Alias</label>
          <input pInputText id="alias" formControlName="alias" />
          <small i18n="@@directions.alias.hint"
            >Minuscules, chiffres et tirets : il figure dans les adresses et dans les jetons.</small
          >
        </div>
        <div class="flex flex-column gap-1">
          <label for="name" i18n="@@directions.name">Nom</label>
          <input pInputText id="name" formControlName="name" />
        </div>
        <div class="flex flex-column gap-1">
          <label for="description" i18n="@@directions.description">Nom complet</label>
          <input pInputText id="description" formControlName="description" />
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
            i18n-label="@@directions.create.submit"
            label="Créer"
          />
        </div>
      </form>
    </p-dialog>
  `,
})
export class Directions {
  private readonly api = inject(DirectionsApi);
  protected readonly user = inject(UserService);
  protected readonly delegations = inject(DelegationsService);

  protected readonly directions = rxResource({ stream: () => this.api.getDirections() });

  private readonly router = inject(Router);
  private readonly notify = injectNotifier();

  protected readonly creating = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    alias: ['', [Validators.required, Validators.pattern(/^[a-z][a-z0-9-]*$/)]],
    name: ['', Validators.required],
    description: [''],
  });

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.DIRECTION)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.directions.reload());
  }

  protected create(): void {
    const { alias, name, description } = this.form.getRawValue();
    this.api
      .createDirection({ alias, name, description: description || undefined })
      .subscribe(() => {
        this.creating.set(false);
        this.form.reset();
        this.notify($localize`:@@directions.created:Direction ${alias}:alias: créée`);
        void this.router.navigate(['/directions', alias]);
      });
  }
}
