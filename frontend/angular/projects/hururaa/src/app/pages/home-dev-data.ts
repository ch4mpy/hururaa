/**
 * The dev environment's dataset, as presented on the home page. It describes what
 * `keycloak/import/public-facing-realm.json` (organizations, groups, their client roles and
 * members) and `hururaa-api`'s Liquibase dev data (`1790700000001-1-dev-data.xml`: applications,
 * direction administrators, application managers) contain: keep the three in sync.
 */

export interface DevApplication {
  name: string;
  clientPrefix: string;
  managers: string[];
}

export interface DevGroup {
  name: string;
  /** Client roles granted to the group's members, by Keycloak client. */
  roles: { clientId: string; roles: string[] }[];
}

export interface DevDirection {
  alias: string;
  admins: string[];
  applications: DevApplication[];
  groups: DevGroup[];
}

export interface DevUser {
  username: string;
  direction: string;
  groups: string[];
}

export const DEV_DIRECTIONS: DevDirection[] = [
  {
    alias: 'dsi',
    admins: ['dsi.admin'],
    applications: [
      { name: "Hurura'a", clientPrefix: 'hururaa', managers: ['dsi.manager'] },
      { name: 'Te Fenua', clientPrefix: 'te-fenua', managers: ['dsi.manager'] },
    ],
    groups: [
      {
        name: 'hururaa-admins',
        roles: [{ clientId: 'hururaa-api', roles: ['hururaa.admin'] }],
      },
      {
        name: 'te-fenua-agents',
        roles: [
          { clientId: 'te-fenua-api', roles: ['te-fenua.parcels.read', 'te-fenua.parcels.edit'] },
        ],
      },
    ],
  },
  {
    alias: 'dpam',
    admins: ['dpam.admin'],
    applications: [{ name: 'Escales', clientPrefix: 'escales', managers: ['dpam.manager'] }],
    groups: [
      {
        name: 'escales-agents',
        roles: [
          { clientId: 'escales-api', roles: ['escales.stopovers.read', 'escales.stopovers.edit'] },
        ],
      },
    ],
  },
  {
    alias: 'daf',
    admins: ['daf.admin'],
    applications: [{ name: 'Anahei', clientPrefix: 'anahei', managers: ['daf.manager'] }],
    groups: [
      {
        name: 'anahei-agents',
        roles: [{ clientId: 'anahei-api', roles: ['anahei.files.read', 'anahei.files.edit'] }],
      },
    ],
  },
];

export const DEV_USERS: DevUser[] = [
  { username: 'hururaa.admin', direction: 'dsi', groups: ['hururaa-admins'] },
  { username: 'dsi.admin', direction: 'dsi', groups: [] },
  { username: 'dsi.manager', direction: 'dsi', groups: [] },
  { username: 'dsi.agent', direction: 'dsi', groups: ['te-fenua-agents'] },
  { username: 'dpam.admin', direction: 'dpam', groups: [] },
  { username: 'dpam.manager', direction: 'dpam', groups: [] },
  { username: 'dpam.agent', direction: 'dpam', groups: ['escales-agents'] },
  { username: 'daf.admin', direction: 'daf', groups: [] },
  { username: 'daf.manager', direction: 'daf', groups: [] },
  { username: 'daf.agent', direction: 'daf', groups: ['anahei-agents'] },
];

/** The delegation a dev user holds, if any (Hurura'a's own comes from the `hururaa-admins` group). */
export function devDelegationOf(
  username: string,
): 'hururaa-admin' | 'admin' | 'manager' | undefined {
  if (username === 'hururaa.admin') {
    return 'hururaa-admin';
  }
  for (const direction of DEV_DIRECTIONS) {
    if (direction.admins.includes(username)) {
      return 'admin';
    }
    if (direction.applications.some((a) => a.managers.includes(username))) {
      return 'manager';
    }
  }
  return undefined;
}
