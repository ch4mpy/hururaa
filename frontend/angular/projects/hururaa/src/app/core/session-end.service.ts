import { Injectable, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ErrorBannerService } from './error-banner.service';
import { ResourceEventsService, ResourceTypes } from './resource-events.service';
import { UserService } from './user.service';

/**
 * Switches the app back to its logged-out state as soon as the gateway announces that the session
 * is over, whatever ended it: idle timeout, logout from another tab, or a logout from another
 * application of the SSO (back-channel logout). Without this, the shell would keep showing the
 * account menu until the next REST call fails.
 *
 * Refreshing the user is what does the work: `/me` then answers anonymous, the header switches
 * back to its login button and `ResourceEventsService` closes the stream by itself. The banner is
 * only there to tell the user why.
 *
 * It lives in its own service because `UserService` cannot depend on `ResourceEventsService`
 * (which depends on it) and because `ResourceEventsService` has no business showing messages.
 * Nothing injects it, so it is instantiated at startup (see `appConfig`).
 */
@Injectable({ providedIn: 'root' })
export class SessionEndService {
  private readonly user = inject(UserService);
  private readonly banner = inject(ErrorBannerService);

  constructor() {
    inject(ResourceEventsService)
      .of(ResourceTypes.SESSION)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        this.user.refresh();
        this.banner.show(
          $localize`:@@session.ended:Votre session a pris fin, veuillez vous reconnecter`,
        );
      });
  }
}
