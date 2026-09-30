import { Component, inject } from '@angular/core';
import { MessageModule } from 'primeng/message';
import { ErrorBannerService } from '../core/error-banner.service';

/**
 * Dismissable banner under the header, announcing the latest failed HTTP call (see
 * `ErrorBannerService` and `httpErrorInterceptor`). Rendered only while there is a message.
 */
@Component({
  selector: 'app-error-banner',
  imports: [MessageModule],
  template: `
    @if (banner.message(); as message) {
      <div class="pf-page-center-container pt-3" role="alert">
        <p-message
          severity="error"
          icon="ri-error-warning-line"
          [closable]="true"
          (onClose)="banner.dismiss()"
          >{{ message }}</p-message
        >
      </div>
    }
  `,
})
export class ErrorBanner {
  protected readonly banner = inject(ErrorBannerService);
}
