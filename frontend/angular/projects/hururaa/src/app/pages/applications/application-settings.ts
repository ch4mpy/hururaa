import { Component, effect, inject, input, numberAttribute } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApplicationsApi } from '@api/hururaa-api';
import { ConfirmationService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { confirm } from '../../core/confirm';
import { DelegationsService } from '../../core/delegations.service';
import { injectNotifier } from '../shared/labels';
import { ApplicationContext } from './application-context';

/**
 * The settings tab of an application, for its direction's administrators: its name, and its
 * unregistration (once it has no groups left; its Keycloak clients are kept).
 */
@Component({
  selector: 'app-application-settings',
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule],
  template: `
    @if (context.canEdit()) {
      <section class="mb-5">
        <h3 class="text-lg mt-0 mb-3" i18n="@@application.identity">Identité</h3>
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
        </form>
      </section>

      <section class="p-3 border-1 border-round border-red-300">
        <h3 class="text-lg mt-0 mb-2" i18n="@@application.unregister">Désenregistrer</h3>
        <p class="mt-0" i18n="@@application.unregister.groupsHint">
          Une application ne peut être désenregistrée tant qu'elle a des groupes.
        </p>
        <p-button
          severity="danger"
          [outlined]="true"
          icon="ri-delete-bin-line"
          i18n-label="@@application.unregister"
          label="Désenregistrer"
          (onClick)="unregister()"
        />
      </section>
    } @else {
      <p i18n="@@application.settings.noSay">
        Seuls les administrateurs de la direction modifient ses applications.
      </p>
    }
  `,
})
export class ApplicationSettings {
  private readonly api = inject(ApplicationsApi);
  private readonly confirmation = inject(ConfirmationService);
  private readonly router = inject(Router);
  private readonly delegations = inject(DelegationsService);
  private readonly notify = injectNotifier();
  protected readonly context = inject(ApplicationContext);

  readonly direction = input.required<string>();
  readonly applicationId = input.required({ transform: numberAttribute });

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', Validators.required],
  });

  constructor() {
    effect(() => {
      const application = this.context.application.value();
      if (application) {
        this.form.reset({ name: application.name });
      }
    });
  }

  protected save(): void {
    const { name } = this.form.getRawValue();
    this.api.updateApplication(this.direction(), this.applicationId(), { name }).subscribe(() => {
      this.notify($localize`:@@application.saved:Application ${name}:name: mise à jour`);
      this.context.application.reload();
      this.delegations.refresh();
    });
  }

  protected unregister(): void {
    const name = this.context.application.value()?.name ?? '';
    confirm(this.confirmation, {
      header: $localize`:@@application.unregister:Désenregistrer`,
      message: $localize`:@@application.unregister.confirm:Désenregistrer l'application ${name}:name: ? Ses clients Keycloak ne sont pas supprimés.`,
    }).subscribe(() =>
      this.api.deleteApplication(this.direction(), this.applicationId()).subscribe(() => {
        this.notify(
          $localize`:@@application.unregistered:Application ${name}:name: désenregistrée`,
        );
        this.delegations.refresh();
        void this.router.navigate(['/applications', this.direction()]);
      }),
    );
  }
}
