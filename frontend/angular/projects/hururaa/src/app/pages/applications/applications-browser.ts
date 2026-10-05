import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ApplicationsApi, DirectionsApi } from '@api/hururaa-api';
import { PfPageComponent } from 'pf-ui';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { DelegationsService } from '../../core/delegations.service';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';
import { DirectionSwitch } from '../shared/direction-switch';
import { injectNotifier } from '../shared/labels';

/**
 * The applications area of a direction, master-detail: the applications the user manages in the
 * direction on the side (where its administrators register new ones), the selected one (or the
 * direction's own page) next to it.
 *
 * The side list is left out for a manager of a single application of the direction, who has
 * nothing to pick in it (see `skipApplicationChoice`).
 */
@Component({
  selector: 'app-applications-browser',
  imports: [
    PfPageComponent,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
    ReactiveFormsModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    DirectionSwitch,
  ],
  styles: `
    .nav-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.625rem 0.75rem;
      border-radius: var(--p-content-border-radius, 6px);
      color: var(--p-text-color);
      text-decoration: none;
    }
    .nav-item:hover {
      background: var(--p-content-hover-background);
    }
    .nav-item.active {
      background: var(--p-highlight-background);
      color: var(--p-highlight-color);
      font-weight: 600;
    }
  `,
  template: `
    <pf-page [withPadding]="true">
      <ng-template #title>{{ details.value()?.name ?? direction().toUpperCase() }}</ng-template>
      <ng-template #toolbar>
        <app-direction-switch area="applications" [direction]="direction()" />
      </ng-template>

      <div class="grid">
        @if (showList()) {
          <aside class="col-12 md:col-4 xl:col-3">
            <nav
              class="flex flex-column gap-1"
              i18n-aria-label="@@browser.nav"
              aria-label="Applications de la direction"
            >
              @if (administers()) {
                <a
                  class="nav-item"
                  [routerLink]="['/applications', direction()]"
                  routerLinkActive="active"
                  [routerLinkActiveOptions]="{ exact: true }"
                >
                  <i class="ri-building-2-line" aria-hidden="true"></i>
                  <span i18n="@@browser.direction">La direction</span>
                </a>
              }
              <h2
                class="text-sm font-semibold uppercase text-color-secondary mt-3 mb-1 px-2"
                i18n="@@browser.applications"
              >
                Applications
              </h2>
              @for (application of applications.value() ?? []; track application.id) {
                <a
                  class="nav-item"
                  [routerLink]="['/applications', direction(), application.id]"
                  routerLinkActive="active"
                >
                  <i class="ri-apps-2-line" aria-hidden="true"></i>
                  <span class="flex flex-column">
                    <span>{{ application.name }}</span>
                    <small class="text-color-secondary font-normal">{{
                      application.clientPrefix
                    }}</small>
                  </span>
                </a>
              } @empty {
                @if (!applications.isLoading()) {
                  <p class="m-0 px-2 text-color-secondary" i18n="@@browser.applications.empty">
                    Aucune application enregistrée
                  </p>
                }
              }
            </nav>
            @if (administers()) {
              <p-button
                styleClass="w-full mt-3"
                [outlined]="true"
                icon="ri-add-line"
                i18n-label="@@applications.register"
                label="Enregistrer une application"
                (onClick)="registering.set(true)"
              />
            }
          </aside>
        }
        <section [class]="showList() ? 'col-12 md:col-8 xl:col-9' : 'col-12'">
          <router-outlet />
        </section>
      </div>
    </pf-page>

    <p-dialog
      [visible]="registering()"
      (visibleChange)="registering.set($event)"
      [modal]="true"
      [style]="{ width: '32rem' }"
      i18n-header="@@applications.register"
      header="Enregistrer une application"
    >
      <form [formGroup]="form" (ngSubmit)="register()" class="flex flex-column gap-3">
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
        <div class="flex justify-content-end gap-2">
          <p-button
            severity="secondary"
            [outlined]="true"
            i18n-label="@@dialog.cancel"
            label="Annuler"
            (onClick)="registering.set(false)"
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
export class ApplicationsBrowser {
  private readonly applicationsApi = inject(ApplicationsApi);
  private readonly directionsApi = inject(DirectionsApi);
  private readonly router = inject(Router);
  private readonly notify = injectNotifier();
  private readonly delegations = inject(DelegationsService);

  /** Bound from the `:direction` route parameter. */
  readonly direction = input.required<string>();

  protected readonly details = rxResource({
    params: () => this.direction(),
    stream: ({ params }) => this.directionsApi.getDirection(params),
  });

  /**
   * The direction's applications the user manages (the API decides: all of them for its
   * administrators).
   */
  protected readonly applications = rxResource({
    params: () => this.direction(),
    stream: ({ params }) => this.applicationsApi.getApplications(params, true),
  });

  /** Whether the user administers the direction: registers its applications, designates managers. */
  protected readonly administers = computed(() =>
    this.delegations.canEditApplicationsOf(this.direction()),
  );

  /** Known from the token, before the applications are loaded: no flicker. */
  protected readonly showList = computed(
    () => this.administers() || this.delegations.managedApplicationsIn(this.direction()).length > 1,
  );

  protected readonly registering = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    clientPrefix: ['', [Validators.required, Validators.pattern(/^[a-z][a-z0-9-]*$/)]],
    name: ['', Validators.required],
  });

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.APPLICATION)
      .pipe(takeUntilDestroyed())
      .subscribe((event) => event.tenant === this.direction() && this.applications.reload());
  }

  protected register(): void {
    const request = this.form.getRawValue();
    const direction = this.direction();
    this.applicationsApi.createApplication(direction, request, 'response').subscribe((response) => {
      this.registering.set(false);
      this.form.reset();
      this.notify(
        $localize`:@@applications.registered:Application ${request.name}:name: enregistrée`,
      );
      this.applications.reload();
      const id = response.headers.get('Location')?.split('/').pop();
      if (id) {
        void this.router.navigate(['/applications', direction, id]);
      }
    });
  }
}
