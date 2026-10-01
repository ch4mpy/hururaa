import { inject } from '@angular/core';
import { UserResponse } from '@api/hururaa-api';
import { MessageService } from 'primeng/api';

/** Full name if known, otherwise the username. */
export function userLabel(user: UserResponse): string {
  return nameLabel(user.username, user.firstName, user.lastName);
}

/** Full name if known, otherwise the username. */
export function nameLabel(username: string, firstName?: string, lastName?: string): string {
  const fullName = [firstName, lastName].filter((s) => !!s).join(' ');
  return fullName || username;
}

/**
 * A function announcing a successful change in the shell's toast (failures go to the error banner,
 * see `httpErrorInterceptor`). To be called in an injection context.
 */
export function injectNotifier(): (detail: string) => void {
  const messages = inject(MessageService);
  return (detail) =>
    messages.add({
      severity: 'success',
      summary: $localize`:@@toast.saved:Enregistré`,
      detail,
      life: 4000,
    });
}
