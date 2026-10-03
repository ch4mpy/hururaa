/**
 * Hurura'a's delegation chain, made of Keycloak roles and groups like the permissions of any
 * application: the {@link pf.hururaa.uaa.HururaaPermission Hurura'a roles} (client roles of
 * {@code hururaa-api}), granted in each direction by the
 * {@link pf.hururaa.uaa.DelegationGroups delegation groups}, which Hurura'a creates with the
 * directions and applications ({@link pf.hururaa.uaa.DelegationService}):
 *
 * <ol>
 * <li><b>Hurura'a administrators</b> are the administrators of the
 * {@link pf.hururaa.uaa.UaaProperties#getPlatformOrganization() DSI}, which runs Hurura'a
 * ({@code hururaa.direction.admin} held in the DSI, turned into an authority): they act in every
 * direction, and alone create directions and designate their administrators;</li>
 * <li><b>direction administrators</b> (members of the direction's {@code hururaa.admins} group,
 * holding {@code hururaa.direction.admin} there) register, rename and unregister the applications
 * of their direction, define their roles and managers, and manage their groups;</li>
 * <li><b>application managers</b> (members of the {@code hururaa.<prefix>.product-owners} group of
 * the application's direction, holding {@code hururaa.application.<prefix>.manage} there) define
 * the roles and the other managers of the applications they manage, create their groups (named
 * after their client prefix: {@code escales.agent}), make them grant their roles and assign users
 * to them.</li>
 * </ol>
 *
 * <p>
 * Delegations are read from the user's token: one granted or revoked takes effect when the
 * delegate's token is renewed, like any application role. The {@code {direction}},
 * {@code {group}} and {@code {applicationId}} path variables are resolved into objects
 * ({@code DelegatedDirection}, {@code DelegatedGroup}, {@code Application}) whose methods the
 * {@code @PreAuthorize} rules call with the {@code Authentication}, to read the Hurura'a roles it
 * holds in the direction concerned.
 * </p>
 *
 * <p>
 * Names starting with {@code hururaa.} are reserved, for roles as for groups: only Hurura'a
 * creates, changes or deletes those, and no endpoint lets a user do so.
 * </p>
 */
@NullMarked
package pf.hururaa.uaa;

import org.jspecify.annotations.NullMarked;
