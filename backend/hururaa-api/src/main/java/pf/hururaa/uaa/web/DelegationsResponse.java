package pf.hururaa.uaa.web;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import pf.hururaa.application.web.ApplicationResponse;

/**
 * What the current user may do in Hurura'a, level by level of the delegation chain.
 *
 * @param hururaaRoles the Hurura'a roles the user holds in the DSI
 * @param platformOrganization alias of the DSI, which runs Hurura'a (property
 *        {@code uaa.platform-organization})
 * @param administeredDirections aliases of the directions the user administers
 * @param managedApplications the applications the user manages
 */
public record DelegationsResponse(
    @NotNull List<String> hururaaRoles,
    @NotNull String platformOrganization,
    @NotNull List<String> administeredDirections,
    @NotNull List<ApplicationResponse> managedApplications) {
}
