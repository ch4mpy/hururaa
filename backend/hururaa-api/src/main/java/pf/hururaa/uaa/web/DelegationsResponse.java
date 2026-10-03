package pf.hururaa.uaa.web;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import pf.hururaa.application.web.ApplicationResponse;

/**
 * What the current user may do in Hurura'a, level by level of the delegation chain, as read from
 * their token.
 *
 * @param hururaaRoles the Hurura'a roles the user holds in the DSI ({@code hururaa.direction.admin}
 *        for a Hurura'a administrator)
 * @param platformOrganization alias of the DSI, which runs Hurura'a (property
 *        {@code uaa.platform-organization})
 * @param administeredDirections aliases of the directions the user holds
 *        {@code hururaa.direction.admin} in (the DSI's administrators administering every direction,
 *        which is not listed)
 * @param managedApplications the applications the user manages
 */
public record DelegationsResponse(
    @NotNull List<String> hururaaRoles,
    @NotNull String platformOrganization,
    @NotNull List<String> administeredDirections,
    @NotNull List<ApplicationResponse> managedApplications) {
}
