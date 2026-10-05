import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DirectionsApi } from '@api/hururaa-api';
import { PfPageComponent } from 'pf-ui';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { DelegationsService } from '../../core/delegations.service';
import { Area } from '../../core/landing.guards';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';
import { injectNotifier } from './labels';

/**
 * The first step of both areas for a user having a say on several directions: picking one of
 * them (users with a single one skip it, see `skipDirectionChoice`). Hurura'a administrators,
 * who see every direction, create new ones here.
 */
@Component({
  selector: 'app-direction-chooser',
  imports: [
    PfPageComponent,
    RouterLink,
    ReactiveFormsModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    TagModule,
  ],
  styles: `
    .direction {
      transition: background-color 0.2s;
    }
    .direction:hover,
    .direction:focus-visible {
      background: var(--p-content-hover-background);
    }
  `,
  template: `
    <pf-page [withPadding]="true">
      <ng-template #title>
        @if (area() === 'users') {
          <span i18n="@@chooser.users.title">Utilisateurs</span>
        } @else {
          <span i18n="@@chooser.applications.title">Applications</span>
        }
      </ng-template>
      @if (area() === 'applications' && delegations.isAdmin()) {
        <ng-template #toolbar>
          <p-button
            icon="ri-add-line"
            i18n-label="@@directions.create"
            label="Créer une direction"
            (onClick)="creating.set(true)"
          />
        </ng-template>
      }

      <p class="mt-0 text-color-secondary" i18n="@@chooser.intro">Choisissez une direction.</p>
      <div class="grid">
        @for (direction of directions(); track direction.alias) {
          <div class="col-12 md:col-6 xl:col-4">
            <a
              class="direction flex flex-column gap-2 h-full p-3 border-1 surface-border border-round no-underline text-color"
              [routerLink]="['/', area(), direction.alias]"
            >
              <span class="flex align-items-center justify-content-between gap-2">
                <span class="text-xl font-semibold">{{ direction.name }}</span>
                <i class="ri-arrow-right-s-line text-xl" aria-hidden="true"></i>
              </span>
              @if (direction.description) {
                <span class="text-color-secondary">{{ direction.description }}</span>
              }
              <span class="flex flex-wrap gap-2 mt-auto">
                @if (delegations.isDirectionAdmin(direction.alias)) {
                  <p-tag severity="warn" i18n-value="@@directions.admin" value="Administrateur" />
                }
                @if (delegations.managedApplicationsIn(direction.alias).length) {
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
              </span>
            </a>
          </div>
        } @empty {
          @if (!all.isLoading()) {
            <p class="col-12" i18n="@@chooser.empty">
              Vous n'administrez aucune direction et ne gérez aucune application.
            </p>
          }
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
export class DirectionChooser {
  private readonly api = inject(DirectionsApi);
  private readonly router = inject(Router);
  private readonly notify = injectNotifier();
  protected readonly delegations = inject(DelegationsService);

  /** Bound from the route's data: the area the chosen direction is opened in. */
  readonly area = input.required<Area>();

  protected readonly all = rxResource({ stream: () => this.api.getDirections() });

  /** The directions the user has a say on. */
  protected readonly directions = computed(() => this.delegations.withSay(this.all.value() ?? []));

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
      .subscribe(() => this.all.reload());
  }

  protected create(): void {
    const { alias, name, description } = this.form.getRawValue();
    this.api
      .createDirection({ alias, name, description: description || undefined })
      .subscribe(() => {
        this.creating.set(false);
        this.form.reset();
        this.notify($localize`:@@directions.created:Direction ${alias}:alias: créée`);
        void this.router.navigate(['/applications', alias]);
      });
  }
}
