import { provideHttpClient, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { TitleStrategy, provideRouter, withComponentInputBinding } from '@angular/router';
import { provideApi as provideGatewayApi } from '@api/gateway';
import { provideApi as provideHururaaApi } from '@api/hururaa-api';
import { TranslateLoader, TranslateService, provideTranslateService } from '@ngx-translate/core';
import { PfUiTranslateLoader, providePFTheme } from 'pf-ui';
import { ConfirmationService, MessageService, Translation } from 'primeng/api';
import { PrimeNG } from 'primeng/config';
import { en } from 'primelocale/js/en.js';
import { fr } from 'primelocale/js/fr.js';
import { routes } from './app.routes';
import { bffInterceptor } from './core/bff.interceptor';
import { httpErrorInterceptor } from './core/http-error.interceptor';
import { PageTitleStrategy } from './core/page-title.strategy';
import { SessionEndService } from './core/session-end.service';

/**
 * The gateway (OAuth2 BFF) is reached through the reverse proxy, on the same origin as this app.
 */
export const GATEWAY_BASE_PATH = '/gateway';

/**
 * The REST API is reached through the gateway's BFF route (`/bff/api/v1/**`), which swaps the
 * session cookie for the access token before relaying the request to `hururaa-api`.
 */
export const HURURAA_API_BASE_PATH = `${GATEWAY_BASE_PATH}/bff/api/v1`;

/** `fr-PF` → `fr`: pf-ui's own labels are translated with ngx-translate, per language. */
export const languageOf = (locale: string): string => locale.split('-')[0];

/**
 * PrimeNG's own labels (close buttons, paginators, empty messages...) and their `aria-label`s, per
 * language: PrimeNG ships English only, `primelocale` is PrimeFaces' official translations.
 */
const PRIMENG_TRANSLATIONS: Record<string, Translation> = { fr, en };

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // route parameters are bound to same-named component inputs (see ApplicationDetail's `applicationId`)
    provideRouter(routes, withComponentInputBinding()),
    { provide: TitleStrategy, useExisting: PageTitleStrategy },
    provideHttpClient(
      // CSRF cookie / header names expected by the gateway
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      // failed calls (network, 4xx, 5xx) are announced in the shell's error banner
      withInterceptors([bffInterceptor, httpErrorInterceptor]),
    ),
    provideGatewayApi(GATEWAY_BASE_PATH),
    provideHururaaApi(HURURAA_API_BASE_PATH),
    // PrimeNG with the design system's preset (Lara based), and animations
    providePFTheme(),
    // Only pf-ui's own labels go through ngx-translate (they ship with the library): this app's
    // texts are compiled per locale with @angular/localize, like any Angular i18n app.
    provideTranslateService({
      loader: { provide: TranslateLoader, useFactory: () => PfUiTranslateLoader.mergeWith() },
      fallbackLang: 'fr',
    }),
    // bootstrap waits for pf-ui's labels in the locale this build was compiled for
    provideAppInitializer(() => inject(TranslateService).use(languageOf(inject(LOCALE_ID)))),
    provideAppInitializer(() => {
      const translation = PRIMENG_TRANSLATIONS[languageOf(inject(LOCALE_ID))];
      if (translation) {
        inject(PrimeNG).setTranslation(translation);
      }
    }),
    MessageService,
    ConfirmationService,
    // nothing injects it: it listens to the gateway's session-end events on its own
    provideAppInitializer(() => void inject(SessionEndService)),
  ],
};
