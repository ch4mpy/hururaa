import { Component, LOCALE_ID, computed, effect, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AvatarService, PfAppComponent } from 'pf-ui';
import { MenuItem } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ToastModule } from 'primeng/toast';
import { languageOf } from './app.config';
import { DelegationsService } from './core/delegations.service';
import { LOCATION } from './core/location';
import { UserService } from './core/user.service';
import { ErrorBanner } from './layout/error-banner';

/** The locales this app is built for, by language (see `i18n` in `angular.json`). */
const LOCALE_SUB_PATHS: Record<string, string> = { fr: 'fr', en: 'en' };

/**
 * Application shell: the design system's `pf-app` (header with the navigation, the account menu and
 * the language selector, footer, mobile tab bar), the error banner and the routed page.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, PfAppComponent, ToastModule, ConfirmDialogModule, ErrorBanner],
  template: `
    <pf-app
      headerTitle="Hurura'a"
      headerDomain="gov.pf"
      headerTitleTargetUrl="/"
      [showSearchBar]="false"
      [headerDesktopTopMenuItems]="accountMenu()"
      [headerDesktopBottomMenuItems]="navigation()"
      [headerPhoneMenuItems]="phoneNavigation()"
      [footerTopLinks]="[]"
      [footerText]="footerText"
      [availableLanguageCodes]="languages"
      [selectedLanguage]="language"
      (selectedLanguageChange)="switchLanguage($event)"
    >
      <app-error-banner />
      <router-outlet />
    </pf-app>
    <p-toast />
    <p-confirmdialog />
  `,
})
export class App {
  private readonly user = inject(UserService);
  private readonly delegations = inject(DelegationsService);
  private readonly location = inject(LOCATION);

  protected readonly language = languageOf(inject(LOCALE_ID));
  protected readonly languages = Object.keys(LOCALE_SUB_PATHS);
  protected readonly footerText = $localize`:@@footer.text:Hurura'a · gestion des accès aux applications des directions de la Polynésie française`;

  protected readonly navigation = computed<MenuItem[]>(() => {
    const items: MenuItem[] = [
      {
        label: $localize`:@@menu.home:Accueil`,
        icon: 'ri-home-4-line',
        routerLink: '/',
        routerLinkActiveOptions: { exact: true },
        visible: true,
      },
    ];
    // the two areas, of no use to a user without any delegation
    if (this.delegations.hasAny()) {
      items.push(
        {
          label: $localize`:@@menu.applications:Applications`,
          icon: 'ri-apps-2-line',
          routerLink: '/applications',
          visible: true,
        },
        {
          label: $localize`:@@menu.users:Utilisateurs`,
          icon: 'ri-team-line',
          routerLink: '/users',
          visible: true,
        },
      );
    }
    return items;
  });

  /** The mobile tab bar only shows the items flagged `visible`, all of the navigation here. */
  protected readonly phoneNavigation = computed<MenuItem[]>(() => [
    ...this.navigation(),
    this.user.isAuthenticated()
      ? {
          label: $localize`:@@header.logout:Déconnexion`,
          icon: 'ri-logout-box-r-line',
          command: () => this.user.logout(),
          visible: true,
        }
      : {
          label: $localize`:@@header.login:Connexion`,
          icon: 'ri-login-box-line',
          command: () => this.user.login(),
          visible: true,
        },
  ]);

  /** The avatar menu once logged in (pf-ui renders the item with id `avatar` as an avatar). */
  protected readonly accountMenu = computed<MenuItem[]>(() => {
    if (!this.user.isAuthenticated()) {
      return [
        {
          label: $localize`:@@header.login:Connexion`,
          icon: 'ri-login-box-line',
          command: () => this.user.login(),
        },
      ];
    }
    const { email } = this.user.current();
    return [
      {
        id: 'avatar',
        label: this.user.displayName(),
        items: [
          { label: email ?? '', icon: 'ri-mail-line', disabled: true },
          { separator: true },
          {
            label: $localize`:@@header.logout:Déconnexion`,
            icon: 'ri-logout-box-r-line',
            command: () => this.user.logout(),
          },
        ],
      },
    ];
  });

  constructor() {
    const avatar = inject(AvatarService);
    effect(() => {
      const { firstName, lastName } = this.user.current();
      avatar.setFullName(firstName ?? '', lastName ?? '');
    });
  }

  /** A build per locale (`/fr/`, `/en/`): switching language is loading the other build. */
  protected switchLanguage(language: string): void {
    const target = LOCALE_SUB_PATHS[language];
    if (!target || language === this.language) {
      return;
    }
    const url = new URL(this.location.href);
    url.pathname = url.pathname.replace(/^\/[^/]+\//, `/${target}/`);
    this.location.assign(url.href);
  }
}
