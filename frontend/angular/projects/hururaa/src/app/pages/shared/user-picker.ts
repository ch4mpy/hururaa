import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DirectionsApi, UserResponse } from '@api/hururaa-api';
import { AutoCompleteCompleteEvent, AutoCompleteModule } from 'primeng/autocomplete';
import { ButtonModule } from 'primeng/button';
import { userLabel } from './labels';

/**
 * Picks a member of a direction by searching its members (username, names or e-mail), then emits
 * it when the user confirms with the button.
 */
@Component({
  selector: 'app-user-picker',
  imports: [FormsModule, AutoCompleteModule, ButtonModule],
  template: `
    <div class="flex flex-wrap align-items-center gap-2">
      <p-autocomplete
        [(ngModel)]="selected"
        [suggestions]="suggestions()"
        (completeMethod)="search($event)"
        [optionLabel]="optionLabel"
        [minLength]="1"
        [delay]="300"
        [forceSelection]="true"
        [inputId]="inputId()"
        [placeholder]="placeholder"
        [ariaLabel]="label()"
      >
        <ng-template #item let-user>
          <div class="flex flex-column">
            <span>{{ labelOf(user) }}</span>
            <small class="text-color-secondary">{{ user.username }}</small>
          </div>
        </ng-template>
      </p-autocomplete>
      <p-button
        icon="ri-user-add-line"
        [label]="label()"
        [disabled]="!isUser(selected)"
        (onClick)="confirm()"
      />
    </div>
  `,
})
export class UserPicker {
  private readonly directions = inject(DirectionsApi);

  /** The direction whose members are searched. */
  readonly direction = input.required<string>();

  /** The confirming button's label (also the search field's accessible name). */
  readonly label = input.required<string>();

  readonly inputId = input<string>('user-picker');

  /** Emitted with the chosen member when the user confirms. */
  readonly picked = output<UserResponse>();

  protected readonly suggestions = signal<UserResponse[]>([]);
  protected readonly placeholder = $localize`:@@userPicker.placeholder:Nom, identifiant ou courriel`;
  protected readonly optionLabel = 'username';
  protected readonly labelOf = userLabel;

  protected selected: UserResponse | string | undefined;

  protected isUser(value: unknown): value is UserResponse {
    return typeof value === 'object' && value !== null && 'id' in value;
  }

  protected search(event: AutoCompleteCompleteEvent): void {
    this.directions
      .getDirectionUsers(this.direction(), event.query, undefined, undefined, undefined, 0, 20)
      .subscribe((page) => this.suggestions.set(page.content ?? []));
  }

  protected confirm(): void {
    if (this.isUser(this.selected)) {
      this.picked.emit(this.selected);
      this.selected = undefined;
    }
  }
}
