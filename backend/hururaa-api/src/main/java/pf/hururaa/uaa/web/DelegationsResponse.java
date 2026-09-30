package pf.hururaa.uaa.web;

import java.util.List;
import jakarta.validation.constraints.NotNull;
import pf.hururaa.application.web.ApplicationResponse;

/**
 * What the current user may do in Hurura'a, level by level of the delegation chain.
 *
 * @param platformPermissions the Hurura'a roles held in the platform organization
 * @param platformOrganization alias of the platform organization
 * @param administeredDirections aliases of the directions the user administers
 * @param managedApplications the applications the user manages
 */
public record DelegationsResponse(
    @NotNull List<String> platformPermissions,
    @NotNull String platformOrganization,
    @NotNull List<String> administeredDirections,
    @NotNull List<ApplicationResponse> managedApplications) {
}
