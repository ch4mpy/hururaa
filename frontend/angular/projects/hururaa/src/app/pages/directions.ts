import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { DirectionsApi } from '@api/hururaa-api';
import { PfPageComponent } from 'pf-ui';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import { DelegationsService } from '../core/delegations.service';
import { UserService } from '../core/user.service';

/** The directions (Keycloak organizations), with the current user's role in each of them. */
@Component({
  selector: 'app-directions',
  imports: [PfPageComponent, RouterLink, CardModule, TagModule],
  template: `
    <pf-page [withPadding]="true">
      <ng-template #title>
        <span i18n="@@directions.title">Directions</span>
      </ng-template>
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
                  <p-tag severity="success" i18n-value="@@directions.manager" value="Gestionnaire" />
                }
                @if (direction.alias === delegations.current().platformOrganization) {
                  <p-tag severity="danger" i18n-value="@@directions.platform" value="Plateforme" />
                }
              </div>
            </p-card>
          </div>
        }
      </div>
    </pf-page>
  `,
})
export class Directions {
  private readonly api = inject(DirectionsApi);
  protected readonly user = inject(UserService);
  protected readonly delegations = inject(DelegationsService);

  protected readonly directions = rxResource({ stream: () => this.api.getDirections() });
}
