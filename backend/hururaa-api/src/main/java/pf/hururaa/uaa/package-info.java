/**
 * Hurura'a's delegation chain:
 *
 * <ol>
 * <li><b>Hurura'a administrators</b> hold {@link pf.hururaa.uaa.HururaaPermission#ADMIN
 * hururaa.admin} in the
 * {@link pf.hururaa.uaa.UaaProperties#getPlatformOrganization() DSI}, which runs Hurura'a (a
 * token role, turned into an authority): they act at every level, and alone create
 * directions, designate their administrators and move applications between directions;</li>
 * <li><b>direction administrators</b> (a {@code DirectionAdmin} row) register, rename and
 * unregister the applications of their direction, and define their roles and managers;</li>
 * <li><b>application managers</b> (a user id in {@code Application.managers}) define the roles and
 * the other managers of the applications they manage, aggregate roles into groups of the
 * direction, and assign users to those groups.</li>
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
