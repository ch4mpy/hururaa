/**
 * Hurura'a's delegation chain:
 *
 * <ol>
 * <li><b>platform administrators</b> hold
 * {@link pf.hururaa.uaa.HururaaPermission Hurura'a's roles} in the
 * {@link pf.hururaa.uaa.UaaProperties#getPlatformOrganization() platform organization} (a token
 * role, turned into an authority): they register applications, assign them to directions, and
 * designate each direction's administrators;</li>
 * <li><b>direction administrators</b> (a {@code DirectionAdmin} row) designate, among the members of
 * their direction, the managers of each of its applications;</li>
 * <li><b>application managers</b> (a user id in {@code Application.managers}) define the
 * application's roles, aggregate them into groups of the direction, and assign users to those
 * groups.</li>
 * </ol>
 *
 * <p>
 * Levels 2 and 3 are read from the database on every request rather than from the token: a
 * delegation takes effect (or is revoked) at once, without the delegate having to log in again.
 * The {@code {direction}}, {@code {group}} and {@code {applicationId}} path variables are resolved
 * into objects carrying these delegations ({@code DelegatedDirection}, {@code DelegatedGroup},
 * {@code Application}), which the {@code @PreAuthorize} rules are written against, with the
 * {@code Authentication}'s name: the user's {@code sub}, the Keycloak user id the delegations are
 * stored with.
 * </p>
 */
@NullMarked
package pf.hururaa.uaa;

import org.jspecify.annotations.NullMarked;
