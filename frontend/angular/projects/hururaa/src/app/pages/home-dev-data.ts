/**
 * The dev environment's dataset, as presented on the home page. It describes what
 * `keycloak/import/public-facing-realm.json` (organizations, groups, their client roles and
 * members, Hurura'a's reserved groups included) and `hururaa-api`'s Liquibase dev data
 * (`1790700000001-1-dev-data.xml`: applications) contain: keep the three in sync. Direction
 * administrators are the members of the direction's `hururaa.admins` group, application managers
 * those of its `hururaa.<prefix>.product-owners` group.
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

function admins(name: string): DevGroup {
  return { name, roles: [{ clientId: 'hururaa-api', roles: ['hururaa.direction.admin'] }] };
}

function productOwners(clientPrefix: string): DevGroup {
  return {
    name: `hururaa.${clientPrefix}.product-owners`,
    roles: [{ clientId: 'hururaa-api', roles: [`hururaa.application.${clientPrefix}.manage`] }],
  };
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
      admins('hururaa.admins'),
      productOwners('hururaa'),
      productOwners('te-fenua'),
      {
        name: 'te-fenua.agent',
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
      admins('hururaa.admins'),
      productOwners('escales'),
      {
        name: 'escales.agent',
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
      admins('hururaa.admins'),
      productOwners('anahei'),
      {
        name: 'anahei.agent',
        roles: [{ clientId: 'anahei-api', roles: ['anahei.files.read', 'anahei.files.edit'] }],
      },
    ],
  },
];

export const DEV_USERS: DevUser[] = [
  { username: 'dsi.admin', direction: 'dsi', groups: ['hururaa.admins'] },
  {
    username: 'dsi.manager',
    direction: 'dsi',
    groups: ['hururaa.hururaa.product-owners', 'hururaa.te-fenua.product-owners'],
  },
  { username: 'dsi.agent', direction: 'dsi', groups: ['te-fenua.agent'] },
  { username: 'dpam.admin', direction: 'dpam', groups: ['hururaa.admins'] },
  { username: 'dpam.manager', direction: 'dpam', groups: ['hururaa.escales.product-owners'] },
  { username: 'dpam.agent', direction: 'dpam', groups: ['escales.agent'] },
  { username: 'daf.admin', direction: 'daf', groups: ['hururaa.admins'] },
  { username: 'daf.manager', direction: 'daf', groups: ['hururaa.anahei.product-owners'] },
  { username: 'daf.agent', direction: 'daf', groups: ['anahei.agent'] },
];
