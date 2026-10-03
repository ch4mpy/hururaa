import { Component } from '@angular/core';
import { PfPageComponent, PfTagComponent } from 'pf-ui';
import { AccordionModule } from 'primeng/accordion';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { DEV_DIRECTIONS, DEV_USERS } from './home-dev-data';

/** A component of the dev environment, as listed on the home page. */
interface DevLink {
  label: string;
  /** Absent for a component without web interface: only its address is shown. */
  url?: string;
  display: string;
  note?: string;
  /** Shown in bold: the components one opens first when trying the PoC. */
  highlight?: boolean;
}

/**
 * Presents Hurura'a: the business first (what it manages, the delegation chain and its rules),
 * then the dev environment's dataset (see `home-dev-data.ts`), links to its software components and
 * the technical solution.
 */
@Component({
  selector: 'app-home',
  imports: [PfPageComponent, PfTagComponent, AccordionModule, CardModule, TableModule],
  styles: `
    dt {
      font-weight: 600;
    }
    dd {
      margin: 0 0 0.75rem;
    }
    li {
      margin-bottom: 0.25rem;
    }
  `,
  template: `
    <pf-page [withPadding]="true">
      <ng-template #title>
        <span i18n="@@home.title">Identification et autorisation des utilisateurs</span>
      </ng-template>

      <p-accordion [(value)]="openPanel">
        <!-- =================================================================== le métier -->
        <p-accordion-panel value="business">
          <p-accordion-header i18n="@@home.business">1. Le métier</p-accordion-header>
          <p-accordion-content>
            <p class="mt-0" i18n="@@home.business.intro">
              Les directions de l'administration polynésienne exploitent chacune leurs applications.
              Hurura'a montre comment chaque direction pourrait gérer elle-même qui peut faire quoi
              avec ses applications, sans passer par les administrateurs Keycloak de la DSI.
            </p>

            <h3 i18n="@@home.vocabulary">Vocabulaire</h3>
            <dl>
              <dt i18n="@@home.vocabulary.direction.term">Direction</dt>
              <dd i18n="@@home.vocabulary.direction">
                Une direction de l'administration (DSI, DPAM, DAF...). Ses agents en sont membres ;
                un agent peut être membre de plusieurs directions.
              </dd>
              <dt i18n="@@home.vocabulary.application.term">Application</dt>
              <dd i18n="@@home.vocabulary.application">
                Un logiciel dont les utilisateurs se connectent avec leur compte de
                l'administration. Chaque application est gérée par une et une seule direction.
              </dd>
              <dt i18n="@@home.vocabulary.role.term">Rôle</dt>
              <dd i18n="@@home.vocabulary.role">
                Un droit défini par une application (consulter les escales, instruire un
                dossier...), que l'application vérifie à chaque action de l'utilisateur.
              </dd>
              <dt i18n="@@home.vocabulary.group.term">Groupe</dt>
              <dd i18n="@@home.vocabulary.applicationGroup">
                Un ensemble d'agents d'une direction, propre à l'une de ses applications dont il
                porte le préfixe (escales.agent) et attribue des rôles : c'est en rejoignant un
                groupe qu'un agent reçoit ses rôles.
              </dd>
            </dl>

            <h3 i18n="@@home.chain">La chaîne de délégation</h3>
            <div class="grid">
              <div class="col-12 md:col-4">
                <p-card>
                  <ng-template #title>
                    <i class="ri-government-line mr-2" aria-hidden="true"></i>
                    <span i18n="@@home.level.hururaaAdmins.title">1. Administrateurs Hurura'a</span>
                  </ng-template>
                  <p class="m-0" i18n="@@home.level.hururaaAdmin.directions">
                    Les administrateurs de la DSI, qui exploite Hurura'a, agissent à tous les
                    niveaux. Eux seuls créent les directions et désignent leurs administrateurs.
                  </p>
                </p-card>
              </div>
              <div class="col-12 md:col-4">
                <p-card>
                  <ng-template #title>
                    <i class="ri-building-2-line mr-2" aria-hidden="true"></i>
                    <span i18n="@@home.level.direction.title">2. Direction</span>
                  </ng-template>
                  <p class="m-0" i18n="@@home.level.direction.applicationGroups">
                    Les administrateurs d'une direction enregistrent ses applications, les renomment
                    ou les désenregistrent, définissent leurs rôles, désignent parmi ses membres
                    leurs gestionnaires et gèrent les groupes de ces applications.
                  </p>
                </p-card>
              </div>
              <div class="col-12 md:col-4">
                <p-card>
                  <ng-template #title>
                    <i class="ri-apps-2-line mr-2" aria-hidden="true"></i>
                    <span i18n="@@home.level.application.title">3. Application</span>
                  </ng-template>
                  <p class="m-0" i18n="@@home.level.application.groups">
                    Les gestionnaires d'une application définissent ses rôles et ses autres
                    gestionnaires, créent ses groupes, leur attribuent ses rôles et y affectent les
                    utilisateurs.
                  </p>
                </p-card>
              </div>
            </div>

            <h3 i18n="@@home.rules">Les règles</h3>
            <ul>
              <li i18n="@@home.rules.groupApplication">
                Un groupe est préfixé par le nom d'une application et n'attribue que ses rôles.
              </li>
              <li i18n="@@home.rules.groupManagers">
                Les rôles et les membres d'un groupe sont gérés par les gestionnaires de son
                application et par les administrateurs de sa direction.
              </li>
              <li i18n="@@home.rules.reserved">
                Les groupes et les rôles dont le nom commence par « hururaa. » sont réservés : seul
                Hurura'a les crée, les modifie ou les supprime.
              </li>
              <li i18n="@@home.rules.tokenRenewal">
                Un rôle, y compris ceux de Hurura'a, prend effet quand le jeton de l'utilisateur
                est renouvelé (au plus tard à l'expiration de son jeton d'accès).
              </li>
            </ul>
          </p-accordion-content>
        </p-accordion-panel>

        <!-- =================================================================== l'environnement de dev -->
        <p-accordion-panel value="dev">
          <p-accordion-header i18n="@@home.data">2. Environnement de dev</p-accordion-header>
          <p-accordion-content>
            <p class="mt-0" i18n="@@home.data.intro">
              Le royaume Keycloak « public-facing » et la base de Hurura'a sont initialisés avec ce
              jeu de données. Tous les utilisateurs ont le mot de passe « secret ».
            </p>

            <p-table [value]="users">
              <ng-template #header>
                <tr>
                  <th i18n="@@home.data.user">Utilisateur</th>
                  <th i18n="@@home.data.direction">Direction</th>
                  <th i18n="@@home.data.groupList">Groupes</th>
                  <th i18n="@@home.data.userRoles">Rôles</th>
                </tr>
              </ng-template>
              <ng-template #body let-user>
                <tr>
                  <td>
                    <code>{{ user.username }}</code>
                  </td>
                  <td>{{ user.direction.toUpperCase() }}</td>
                  <td>
                    <div class="flex flex-wrap gap-1">
                      @for (group of user.groups; track group) {
                        <pf-tag [value]="group" severity="info" />
                      }
                    </div>
                  </td>
                  <td>
                    <div class="flex flex-wrap gap-1">
                      @for (role of user.roles; track role) {
                        <pf-tag [value]="role" severity="success" />
                      }
                    </div>
                  </td>
                </tr>
              </ng-template>
            </p-table>
          </p-accordion-content>
        </p-accordion-panel>

        <!-- =================================================================== les composants logiciels -->
        <p-accordion-panel value="components">
          <p-accordion-header i18n="@@home.data.links">3. Composants logiciels</p-accordion-header>
          <p-accordion-content>
            <p class="mt-0" i18n="@@home.data.links.intro">
              Chaque lien s'ouvre dans un nouvel onglet. L'environnement de dev doit être lancé
              (voir le README).
            </p>
            <p-table [value]="links">
              <ng-template #header>
                <tr>
                  <th i18n="@@home.data.links.component">Composant</th>
                  <th i18n="@@home.data.links.access">Accès</th>
                  <th i18n="@@home.data.links.note">Remarque</th>
                </tr>
              </ng-template>
              <ng-template #body let-link>
                <tr>
                  <td>
                    @if (link.highlight) {
                      <strong>{{ link.label }}</strong>
                    } @else {
                      {{ link.label }}
                    }
                  </td>
                  <td>
                    @if (link.url) {
                      <a [href]="link.url" target="_blank" rel="noopener noreferrer"
                        ><code>{{ link.display }}</code>
                        <i class="ri-external-link-line ml-1" aria-hidden="true"></i>
                        <span class="sr-only" i18n="@@home.data.links.newTab"
                          >(nouvel onglet)</span
                        ></a
                      >
                    } @else {
                      <code>{{ link.display }}</code>
                    }
                  </td>
                  <td>{{ link.note }}</td>
                </tr>
              </ng-template>
            </p-table>
          </p-accordion-content>
        </p-accordion-panel>

        <!-- =================================================================== la solution technique -->
        <p-accordion-panel value="solution">
          <p-accordion-header i18n="@@home.solution">4. Solution technique</p-accordion-header>
          <p-accordion-content>
            <div class="grid">
              <div class="col-12 md:col-6">
                <p-card>
                  <ng-template #title>
                    <i class="ri-shield-keyhole-line mr-2" aria-hidden="true"></i>
                    <span i18n="@@home.solution.identity.title">Identité : Keycloak</span>
                  </ng-template>
                  <ul class="m-0 pl-3">
                    <li i18n="@@home.solution.identity.realm">
                      Keycloak 26, un seul royaume (public-facing) ; une organisation par direction.
                    </li>
                    <li i18n="@@home.solution.identity.clients">
                      Deux clients OAuth2 par application : « préfixe-bff » pour la connexion des
                      utilisateurs (authorization code avec PKCE, refresh token) et « préfixe-api »
                      pour son API (client credentials), qui porte les rôles de l'application.
                    </li>
                    <li i18n="@@home.solution.identity.claim">
                      Les rôles des groupes d'une organisation sont rangés, dans les jetons, sous la
                      claim « organization » de cette direction.
                    </li>
                  </ul>
                </p-card>
              </div>
              <div class="col-12 md:col-6">
                <p-card>
                  <ng-template #title>
                    <i class="ri-server-line mr-2" aria-hidden="true"></i>
                    <span i18n="@@home.solution.backend.title">Backend : Spring Boot</span>
                  </ng-template>
                  <ul class="m-0 pl-3">
                    <li i18n="@@home.solution.backend.gateway">
                      BFF OAuth2 (Spring Cloud Gateway, oauth2Login, TokenRelay) : le navigateur n'a
                      qu'un cookie de session, jamais de jeton.
                    </li>
                    <li i18n="@@home.solution.backend.api">
                      API REST Hurura'a (Spring Boot 4, Java 26) : pilote l'Admin API de Keycloak
                      avec un client généré depuis sa spec OpenAPI, et applique les règles de la
                      chaîne de délégation.
                    </li>
                    <li i18n="@@home.solution.backend.data">
                      PostgreSQL avec Liquibase et Hibernate Envers pour les applications (leur
                      direction, que Keycloak ne sait pas porter) et le journal des permissions
                      modifiées par Hurura'a.
                    </li>
                    <li i18n="@@home.solution.backend.events">
                      Événements RabbitMQ relayés en Server-Sent Events par le BFF : les écrans se
                      rafraîchissent quand un autre utilisateur modifie ce qu'ils affichent.
                    </li>
                    <li i18n="@@home.solution.backend.errors">
                      Erreurs au format RFC 9457 (problem details), traduites par le frontend.
                    </li>
                  </ul>
                </p-card>
              </div>
              <div class="col-12 md:col-6">
                <p-card>
                  <ng-template #title>
                    <i class="ri-window-line mr-2" aria-hidden="true"></i>
                    <span i18n="@@home.solution.frontend.title">Frontend : Angular</span>
                  </ng-template>
                  <ul class="m-0 pl-3">
                    <li i18n="@@home.solution.frontend.angular">
                      Angular 21 (composants standalone, zoneless, signaux), une build par langue
                      (français, anglais).
                    </li>
                    <li i18n="@@home.solution.frontend.i18n">
                      Entièrement internationalisée, libellés d'accessibilité (aria-label) et
                      messages d'erreur du serveur compris : les erreurs de l'API sont des types de
                      problème fermés, que le frontend traduit lui-même, paramètres inclus ; les
                      libellés internes de PrimeNG et de pf-ui suivent la langue de la build.
                    </li>
                    <li i18n="@@home.solution.frontend.ui">
                      Composants du design system de la Polynésie française (pf-ui, basé sur PrimeNG
                      21 et PrimeFlex) et icônes Remix Icon.
                    </li>
                    <li i18n="@@home.solution.frontend.clients">
                      Clients HTTP générés depuis les specs OpenAPI du BFF et de l'API.
                    </li>
                  </ul>
                </p-card>
              </div>
              <div class="col-12 md:col-6">
                <p-card>
                  <ng-template #title>
                    <i class="ri-stack-line mr-2" aria-hidden="true"></i>
                    <span i18n="@@home.solution.infra.title">Infrastructure de dev</span>
                  </ng-template>
                  <ul class="m-0 pl-3">
                    <li i18n="@@home.solution.infra.compose">
                      Docker Compose : Keycloak, PostgreSQL, RabbitMQ, Mailpit et un reverse proxy
                      nginx qui sert tout sur la même origine.
                    </li>
                    <li i18n="@@home.solution.infra.observability">
                      Observabilité OpenTelemetry vers Grafana LGTM (logs, traces, métriques).
                    </li>
                  </ul>
                </p-card>
              </div>
            </div>
          </p-accordion-content>
        </p-accordion-panel>
      </p-accordion>
    </pf-page>
  `,
})
export class Home {
  /**
   * The only open accordion panel (opening one closes the others): the business when the page is
   * displayed.
   */
  protected openPanel = 'business';

  /** The dev users, with the roles their groups grant them in their direction. */
  protected readonly users = DEV_USERS.map((user) => {
    const groups = DEV_DIRECTIONS.find((d) => d.alias === user.direction)?.groups ?? [];
    const roles = groups
      .filter((g) => user.groups.includes(g.name))
      .flatMap((g) => g.roles.flatMap((r) => r.roles));
    return { ...user, roles: [...new Set(roles)].sort() };
  });

  /**
   * Where each component of the dev environment answers (see the README): on the same origin as
   * this app, through the nginx reverse proxy, except RabbitMQ's console and PostgreSQL.
   */
  protected readonly links: DevLink[] = [
    {
      label: $localize`:@@home.data.links.spa:Frontend (français)`,
      url: '/fr/',
      display: '/fr/',
      highlight: true,
    },
    { label: $localize`:@@home.data.links.spaEn:Frontend (anglais)`, url: '/en/', display: '/en/' },
    {
      label: $localize`:@@home.data.links.bff:BFF : utilisateur courant`,
      url: '/gateway/me',
      display: '/gateway/me',
    },
    {
      label: $localize`:@@home.data.links.api:API, à travers le BFF : applications`,
      url: '/gateway/bff/api/v1/applications',
      display: '/gateway/bff/api/v1/applications',
      note: $localize`:@@home.data.links.api.note:Nécessite d'être connecté`,
    },
    {
      label: $localize`:@@home.data.links.keycloakAdmin:Keycloak : console d'administration`,
      url: '/auth/admin/master/console/#/public-facing',
      display: '/auth/admin',
      note: $localize`:@@home.data.links.keycloakAdmin.note:admin / secret`,
      highlight: true,
    },
    {
      label: $localize`:@@home.data.links.keycloakAccount:Keycloak : espace compte du royaume`,
      url: '/auth/realms/public-facing/account',
      display: '/auth/realms/public-facing/account',
    },
    {
      label: $localize`:@@home.data.links.oidc:Keycloak : configuration OpenID`,
      url: '/auth/realms/public-facing/.well-known/openid-configuration',
      display: '/auth/realms/public-facing/.well-known/openid-configuration',
    },
    {
      label: $localize`:@@home.data.links.rabbitmq:RabbitMQ : console de gestion`,
      url: 'http://localhost:15672/',
      display: 'http://localhost:15672',
      note: $localize`:@@home.data.links.rabbitmq.note:Identifiants dans le fichier .env`,
    },
    {
      label: $localize`:@@home.data.links.mailpit:Mailpit : courriels envoyés par Keycloak`,
      url: '/mailpit/',
      display: '/mailpit/',
      highlight: true,
    },
    {
      label: $localize`:@@home.data.links.grafana:Grafana : logs, traces et métriques`,
      url: '/grafana/',
      display: '/grafana/',
      highlight: true,
    },
    {
      label: $localize`:@@home.data.links.postgres:PostgreSQL : base de Hurura'a`,
      display: 'jdbc:postgresql://localhost:2633/rest_api',
      note: $localize`:@@home.data.links.postgres.note:Pas d'interface web ; mot de passe dans le fichier .env`,
    },
  ];
}
