import realmExport from '../../../../../../../keycloak/import/public-facing-realm.json';
import { DEV_DIRECTIONS, DEV_USERS } from './home-dev-data';

interface RealmGroup {
  id: string;
  name: string;
  clientRoles?: Record<string, string[]>;
}

interface Realm {
  organizations: {
    alias: string;
    groups: RealmGroup[];
    members: { username: string; groups: string[] }[];
  }[];
}

/**
 * The home page presents the dev dataset statically: it must describe what the realm export
 * actually imports.
 */
describe('home page dev dataset', () => {
  const realm = realmExport as unknown as Realm;

  it('lists the organizations, their groups and the roles each group grants', () => {
    const fromRealm = realm.organizations.map((org) => ({
      alias: org.alias,
      groups: org.groups
        .map((g) => ({ name: g.name, roles: g.clientRoles ?? {} }))
        .sort((a, b) => a.name.localeCompare(b.name)),
    }));
    const fromPage = DEV_DIRECTIONS.map((d) => ({
      alias: d.alias,
      groups: d.groups
        .map((g) => ({
          name: g.name,
          roles: Object.fromEntries(g.roles.map((r) => [r.clientId, r.roles])),
        }))
        .sort((a, b) => a.name.localeCompare(b.name)),
    }));

    expect(normalize(fromPage)).toEqual(normalize(fromRealm));
  });

  it('lists every member with their direction and groups', () => {
    const fromRealm = realm.organizations.flatMap((org) =>
      org.members.map((m) => ({
        username: m.username,
        direction: org.alias,
        groups: m.groups.map((id) => org.groups.find((g) => g.id === id)?.name ?? id).sort(),
      })),
    );
    const fromPage = DEV_USERS.map((u) => ({ ...u, groups: [...u.groups].sort() }));

    expect(sortByUsername(fromPage)).toEqual(sortByUsername(fromRealm));
  });

  it('lists as administrators and managers the members of the delegation groups', () => {
    const membersOf = (direction: string, group: string) => {
      const org = realm.organizations.find((o) => o.alias === direction);
      const id = org?.groups.find((g) => g.name === group)?.id;
      return (org?.members ?? [])
        .filter((m) => id !== undefined && m.groups.includes(id))
        .map((m) => m.username)
        .sort();
    };
    for (const direction of DEV_DIRECTIONS) {
      expect([...direction.admins].sort()).toEqual(membersOf(direction.alias, 'hururaa.admins'));
      for (const application of direction.applications) {
        expect([...application.managers].sort()).toEqual(
          membersOf(direction.alias, `hururaa.${application.clientPrefix}.product-owners`),
        );
      }
    }
  });
});

/** Order-insensitive comparison of directions and role lists. */
function normalize(
  directions: { alias: string; groups: { name: string; roles: Record<string, string[]> }[] }[],
) {
  return [...directions]
    .sort((a, b) => a.alias.localeCompare(b.alias))
    .map((d) => ({
      ...d,
      groups: d.groups.map((g) => ({
        name: g.name,
        roles: Object.fromEntries(
          Object.entries(g.roles).map(([client, roles]) => [client, [...roles].sort()]),
        ),
      })),
    }));
}

function sortByUsername<T extends { username: string }>(users: T[]): T[] {
  return [...users].sort((a, b) => a.username.localeCompare(b.username));
}
