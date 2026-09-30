import { ConfirmationService } from 'primeng/api';
import { Observable } from 'rxjs';

export interface ConfirmOptions {
  header: string;
  message: string;
  /** Label of the confirming button, defaults to "Confirmer". */
  acceptLabel?: string;
}

/**
 * Asks the user to confirm an action (typically a deletion), with the shell's `p-confirmdialog`.
 *
 * @returns emits (then completes) only if the user confirmed
 */
export function confirm(service: ConfirmationService, options: ConfirmOptions): Observable<void> {
  return new Observable<void>((subscriber) => {
    service.confirm({
      header: options.header,
      message: options.message,
      icon: 'ri-error-warning-line',
      acceptLabel: options.acceptLabel ?? $localize`:@@dialog.confirm:Confirmer`,
      rejectLabel: $localize`:@@dialog.cancel:Annuler`,
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', outlined: true },
      accept: () => {
        subscriber.next();
        subscriber.complete();
      },
      reject: () => subscriber.complete(),
    });
  });
}
