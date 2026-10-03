import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import {
  ApplicationsApi,
  DirectionsApi,
  GroupsApi,
  PagedModelPermissionChangeResponse,
  PermissionChangeResponse,
  PermissionChangeResponseCategoryEnum,
  PermissionChangeResponseTypeEnum,
} from '@api/hururaa-api';
import { MultiSelectModule } from 'primeng/multiselect';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { Observable, merge } from 'rxjs';
import { ResourceEventsService, ResourceTypes } from '../../core/resource-events.service';
import { nameLabel } from './labels';

const PAGE_SIZE = 10;

type Category = PermissionChangeResponseCategoryEnum;
const Category = PermissionChangeResponseCategoryEnum;
const Type = PermissionChangeResponseTypeEnum;

const CATEGORY_LABELS: Record<Category, () => string> = {
  [Category.direction]: () => $localize`:@@history.category.direction:Directions`,
  [Category.delegation]: () => $localize`:@@history.category.delegation:Administrateurs et gestionnaires`,
  [Category.application]: () => $localize`:@@history.category.application:Applications`,
  [Category.applicationRole]: () =>
    $localize`:@@history.category.applicationRole:Rôles des applications`,
  [Category.group]: () => $localize`:@@history.category.group:Groupes`,
  [Category.groupRole]: () => $localize`:@@history.category.groupRole:Rôles des groupes`,
  [Category.groupMember]: () => $localize`:@@history.category.groupMember:Membres des groupes`,
};

/** One sentence per change type; exhaustive, so a new type fails to compile until described. */
const DESCRIPTIONS: Record<
  PermissionChangeResponseTypeEnum,
  (c: PermissionChangeResponse) => string
> = {
  [Type.directionCreated]: () => $localize`:@@history.directionCreated:Direction créée`,
  [Type.directionAdminGranted]: () =>
    $localize`:@@history.directionAdminGranted:Désigné administrateur de la direction`,
  [Type.directionAdminRevoked]: () =>
    $localize`:@@history.directionAdminRevoked:Retiré des administrateurs de la direction`,
  [Type.applicationManagerGranted]: (c) =>
    $localize`:@@history.applicationManagerGranted:Désigné gestionnaire de ${app(c)}:application:`,
  [Type.applicationManagerRevoked]: (c) =>
    $localize`:@@history.applicationManagerRevoked:Retiré des gestionnaires de ${app(c)}:application:`,
  [Type.applicationRegistered]: (c) =>
    $localize`:@@history.applicationRegistered:Application ${app(c)}:application: enregistrée`,
  [Type.applicationRenamed]: (c) =>
    $localize`:@@history.applicationRenamed:Application ${c.formerApplicationName ?? ''}:former: renommée ${app(c)}:application:`,
  [Type.applicationUnregistered]: (c) =>
    $localize`:@@history.applicationUnregistered:Application ${app(c)}:application: désenregistrée`,
  [Type.applicationRoleCreated]: (c) =>
    $localize`:@@history.applicationRoleCreated:Rôle ${c.role ?? ''}:role: défini pour ${app(c)}:application:`,
  [Type.applicationRoleDeleted]: (c) =>
    $localize`:@@history.applicationRoleDeleted:Rôle ${c.role ?? ''}:role: de ${app(c)}:application: supprimé`,
  [Type.groupCreated]: (c) =>
    $localize`:@@history.groupCreated:Groupe ${c.group ?? ''}:group: créé`,
  [Type.groupDeleted]: (c) =>
    $localize`:@@history.groupDeleted:Groupe ${c.group ?? ''}:group: supprimé`,
  [Type.groupRoleGranted]: (c) =>
    $localize`:@@history.groupRoleGranted:Le groupe ${c.group ?? ''}:group: attribue le rôle ${c.role ?? ''}:role: de ${app(c)}:application:`,
  [Type.groupRoleRevoked]: (c) =>
    $localize`:@@history.groupRoleRevoked:Le groupe ${c.group ?? ''}:group: n'attribue plus le rôle ${c.role ?? ''}:role: de ${app(c)}:application:`,
  [Type.groupMemberAdded]: (c) =>
    $localize`:@@history.groupMemberAdded:Ajouté au groupe ${c.group ?? ''}:group:`,
  [Type.groupMemberRemoved]: (c) =>
    $localize`:@@history.groupMemberRemoved:Retiré du groupe ${c.group ?? ''}:group:`,
};

function app(change: PermissionChangeResponse): string {
  return change.applicationName ?? '';
}

/**
 * Who changed what permissions, and when, newest first: of a direction, of one of its
 * applications, or of one of its groups. Filtered by category, reloaded on every change in the
 * direction.
 */
@Component({
  selector: 'app-permission-history',
  imports: [DatePipe, FormsModule, MultiSelectModule, TableModule],
  template: `
    <div class="flex align-items-center gap-2 mb-2">
      <label for="history-categories" i18n="@@history.categories">Afficher</label>
      <p-multiselect
        inputId="history-categories"
        [options]="categoryOptions"
        optionLabel="label"
        optionValue="value"
        [ngModel]="categories()"
        (ngModelChange)="filter($event)"
        i18n-placeholder="@@history.categories.all"
        placeholder="Tous les changements"
        display="chip"
      />
    </div>
    <p-table
      [value]="history.value()?.content ?? []"
      [lazy]="true"
      [paginator]="true"
      [rows]="pageSize"
      [first]="page() * pageSize"
      [totalRecords]="history.value()?.page?.totalElements ?? 0"
      [loading]="history.isLoading()"
      (onLazyLoad)="load($event)"
    >
      <ng-template #header>
        <tr>
          <th i18n="@@history.date">Date</th>
          <th i18n="@@history.author">Par</th>
          <th i18n="@@history.change">Changement</th>
          <th i18n="@@history.subject">Personne concernée</th>
        </tr>
      </ng-template>
      <ng-template #body let-change>
        <tr>
          <td>{{ change.timestamp | date: 'short' }}</td>
          <td>{{ authorOf(change) }}</td>
          <td>{{ describe(change) }}</td>
          <td>{{ subjectOf(change) }}</td>
        </tr>
      </ng-template>
      <ng-template #emptymessage>
        <tr>
          <td colspan="4" i18n="@@history.empty">Aucun changement enregistré</td>
        </tr>
      </ng-template>
    </p-table>
  `,
})
export class PermissionHistory {
  private readonly directionsApi = inject(DirectionsApi);
  private readonly applicationsApi = inject(ApplicationsApi);
  private readonly groupsApi = inject(GroupsApi);
  protected readonly pageSize = PAGE_SIZE;

  /** The direction whose history to show. */
  readonly direction = input.required<string>();

  /** To show only the history of one of the direction's applications. */
  readonly applicationId = input<number>();

  /** To show only the history of one of the direction's groups. */
  readonly group = input<string>();

  protected readonly categoryOptions = Object.values(Category).map((value) => ({
    value,
    label: CATEGORY_LABELS[value](),
  }));

  protected readonly categories = signal<Category[]>([]);
  protected readonly page = signal(0);

  private readonly params = computed(() => ({
    direction: this.direction(),
    applicationId: this.applicationId(),
    group: this.group(),
    categories: new Set(this.categories()),
    page: this.page(),
  }));

  protected readonly history = rxResource({
    params: () => this.params(),
    stream: ({ params }) => this.fetch(params),
  });

  constructor() {
    const events = inject(ResourceEventsService);
    merge(
      events.of(ResourceTypes.DIRECTION),
      events.of(ResourceTypes.APPLICATION),
      events.of(ResourceTypes.GROUP),
    )
      .pipe(takeUntilDestroyed())
      .subscribe((event) => event.tenant === this.direction() && this.history.reload());
  }

  protected filter(categories: Category[]): void {
    this.page.set(0);
    this.categories.set(categories);
  }

  protected load(event: TableLazyLoadEvent): void {
    this.page.set(Math.floor((event.first ?? 0) / PAGE_SIZE));
  }

  protected authorOf(change: PermissionChangeResponse): string {
    return change.authorUsername
      ? nameLabel(change.authorUsername, change.authorFirstName, change.authorLastName)
      : $localize`:@@history.unknownAuthor:Inconnu`;
  }

  protected subjectOf(change: PermissionChangeResponse): string {
    return change.subjectUsername
      ? nameLabel(change.subjectUsername, change.subjectFirstName, change.subjectLastName)
      : '';
  }

  protected describe(change: PermissionChangeResponse): string {
    return DESCRIPTIONS[change.type](change);
  }

  private fetch(params: {
    direction: string;
    applicationId?: number;
    group?: string;
    categories: Set<Category>;
    page: number;
  }): Observable<PagedModelPermissionChangeResponse> {
    const { direction, applicationId, group, categories, page } = params;
    if (applicationId !== undefined) {
      return this.applicationsApi.getApplicationHistory(
        direction,
        applicationId,
        categories,
        page,
        PAGE_SIZE,
      );
    }
    if (group !== undefined) {
      return this.groupsApi.getGroupHistory(direction, group, categories, page, PAGE_SIZE);
    }
    return this.directionsApi.getDirectionHistory(direction, categories, page, PAGE_SIZE);
  }
}
