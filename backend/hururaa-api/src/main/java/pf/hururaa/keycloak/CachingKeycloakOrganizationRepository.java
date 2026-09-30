package pf.hururaa.keycloak;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.keycloak.admin.api.OrganizationsApi;
import org.keycloak.admin.model.MemberRepresentation;
import org.keycloak.admin.model.OrganizationRepresentation;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.HttpClientErrorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pf.hururaa.problem.ProblemType;
import pf.hururaa.problem.HururaaProblemException;

/**
 * Facade in front of Keycloak's {@link OrganizationsApi} for <b>organizations</b> (Hurura'a
 * directions) and their members, that provides:
 * <ul>
 * <li>caching</li>
 * <li>Mapping of the {@link org.springframework.web.client.RestClient RestClient's}
 * {@link HttpClientErrorException} to a checked {@link HururaaProblemException} ({@link
 * ProblemType#IDENTITY_PROVIDER_ERROR}, or a business type where a {@code 404} has one)</li>
 * </ul>
 *
 * @see CachingKeycloakOrganizationGroupRepository CachingKeycloakOrganizationGroupRepository for
 *      organization groups
 */
@Repository
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = {CachingKeycloakOrganizationRepository.ORGANIZATIONS_CACHE})
class CachingKeycloakOrganizationRepository {
  static final String ORGANIZATIONS_CACHE = "organizations";

  private final KeycloakAdminApiProperties apiProperties;

  private final OrganizationsApi organizationsApi;

  /**
   * @return every organization of the realm (Hurura'a directions), whatever their number
   */
  @Cacheable(cacheNames = ORGANIZATIONS_CACHE, key = "'*'")
  public List<OrganizationRepresentation> findAll() throws HururaaProblemException {
    try {
      final var response = organizationsApi
          .adminRealmsRealmOrganizationsGet(
              apiProperties.getRealmName(),
              Optional.of(false), // briefRepresentation: the description is displayed
              Optional.empty(), // exact
              Optional.of(0), // first
              Optional.of(Integer.MAX_VALUE), // max
              Optional.empty(), // q
              Optional.empty());// search
      return response.getBody() == null ? List.of() : response.getBody();
    } catch (HttpClientErrorException e) {
      log.error("Failed to retrieve organizations: {}", e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching organizations: %s".formatted(e.getMessage()),
          Map.of());
    }
  }

  /**
   * @param orgId
   * @param userId
   * @return the member, empty when the user is not a member of the organization (or does not
   *         exist at all)
   */
  public Optional<MemberRepresentation> findMember(String orgId, String userId)
      throws HururaaProblemException {
    try {
      return Optional
          .ofNullable(
              organizationsApi
                  .adminRealmsRealmOrganizationsOrgIdMembersMemberIdGet(
                      apiProperties.getRealmName(),
                      orgId,
                      userId)
                  .getBody());
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    } catch (HttpClientErrorException e) {
      log
          .error(
              "Failed to retrieve member {} of organization {}: {}",
              userId,
              orgId,
              e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching member %s of organization %s: %s"
              .formatted(userId, orgId, e.getMessage()),
          Map.of());
    }
  }

  @Cacheable(cacheNames = ORGANIZATIONS_CACHE, key = "#orgName")
  public Optional<OrganizationRepresentation> findByName(String orgName)
      throws HururaaProblemException {
    try {
      final var response = organizationsApi
          .adminRealmsRealmOrganizationsGet(
              apiProperties.getRealmName(),
              Optional.empty(), // briefRepresentation
              Optional.of(true), // exact
              Optional.empty(), // first
              Optional.of(1), // max
              Optional.empty(), // q
              Optional.of(orgName));// search

      final List<OrganizationRepresentation> orgs =
          response.getBody() == null || response.getBody().isEmpty() ? List.of()
              : response.getBody();

      return orgs.isEmpty() ? Optional.empty() : Optional.of(orgs.get(0));
    } catch (HttpClientErrorException e) {
      log.error("Failed to retrieve organization named {}: {}", orgName, e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching organization named %s: %s".formatted(orgName, e.getMessage()),
          Map.of());
    }
  }

  @Cacheable(cacheNames = ORGANIZATIONS_CACHE,
      key = "#orgName + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
  public Page<OrganizationRepresentation> findAllByNameContaining(
      String orgName,
      Pageable pageable) throws HururaaProblemException {
    try {
      final var response = organizationsApi
          .adminRealmsRealmOrganizationsGet(
              apiProperties.getRealmName(),
              Optional.empty(), // briefRepresentation
              Optional.of(false), // exact
              Optional.of(pageable.getPageNumber() * pageable.getPageSize()), // first
              Optional.of(pageable.getPageSize()), // max
              Optional.empty(), // q
              Optional.of(orgName));// search

      final List<OrganizationRepresentation> orgs =
          response.getBody() == null || response.getBody().isEmpty() ? List.of()
              : response.getBody();

      return new PageImpl<>(
          orgs,
          pageable,
          orgs.size() < pageable.getPageSize() && pageable.getPageNumber() == 0 ? orgs.size()
              : countOrgs(orgName));
    } catch (HttpClientErrorException e) {
      log.error("Failed to retrieve organizations named {}: {}", orgName, e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching organizations named %s: %s".formatted(orgName, e.getMessage()),
          Map.of());
    }
  }

  /**
   * @param orgId
   * @param search A String representing either a member's username, e-mail, first name, or last
   *        name
   * @param pageable page index and size; sort is ignored
   * @return the organization's members
   */
  public Page<MemberRepresentation> findMembers(String orgId, String search, Pageable pageable)
      throws HururaaProblemException {
    try {
      final var response = organizationsApi
          .adminRealmsRealmOrganizationsOrgIdMembersGet(
              apiProperties.getRealmName(),
              orgId,
              Optional.empty(), // briefRepresentation
              Optional.empty(), // exact
              Optional.of((int) pageable.getOffset()), // first
              Optional.of(pageable.getPageSize()), // max
              Optional.empty(), // membershipType
              search == null || search.isBlank() ? Optional.empty() : Optional.of(search));

      final List<MemberRepresentation> members =
          response.getBody() == null ? List.of() : response.getBody();

      return new PageImpl<>(
          members,
          pageable,
          pageable.getOffset() + members.size()
              + (members.size() == pageable.getPageSize() ? 1 : 0));
    } catch (HttpClientErrorException e) {
      log.error("Failed to retrieve members of organization {}: {}", orgId, e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while fetching members of organization %s: %s".formatted(orgId, e.getMessage()),
          Map.of());
    }
  }

  Long countOrgs(String orgName) throws HururaaProblemException {
    try {
      final var response = organizationsApi
          .adminRealmsRealmOrganizationsCountGet(
              apiProperties.getRealmName(),
              Optional.empty(), // exact
              Optional.empty(), // q
              Optional.of(orgName));// search

      return response.getBody() == null ? 0L : response.getBody();

    } catch (HttpClientErrorException e) {
      log.error("Failed to count organizations named {}: {}", orgName, e.getMessage());
      throw new HururaaProblemException(
          ProblemType.IDENTITY_PROVIDER_ERROR,
          "Error while counting organizations named %s: %s".formatted(orgName, e.getMessage()),
          Map.of());
    }
  }
}
