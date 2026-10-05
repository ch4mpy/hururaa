import { Component, input, numberAttribute } from '@angular/core';
import { PermissionHistory } from '../shared/permission-history';

/** The history tab of an application: who changed its roles, groups and managers. */
@Component({
  selector: 'app-application-history',
  imports: [PermissionHistory],
  template: `<app-permission-history
    [direction]="direction()"
    [applicationId]="applicationId()"
  />`,
})
export class ApplicationHistory {
  readonly direction = input.required<string>();
  readonly applicationId = input.required({ transform: numberAttribute });
}
