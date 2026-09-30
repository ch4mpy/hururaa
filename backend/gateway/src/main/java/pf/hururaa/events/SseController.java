package pf.hururaa.events;

import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import pf.hururaa.commons.events.ResourceEvent;
import pf.hururaa.commons.security.HururaaAuthentication;

import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Tag(name = "Gateway")
@RestController
@RequiredArgsConstructor
@Observed
@Slf4j
public class SseController {
  public static final String TENANT_PLACEHOLDER = "tenant";
  public static final String BASE_PATH = "/bff/events/{" + TENANT_PLACEHOLDER + "}";

  private final SseEmitterRegistry registry;

  private final SseProperties properties;

  /**
   * Subscribes the current user to the notification stream of a tenant: a {@link ResourceEvent}
   * is pushed whenever something they are entitled to see happens on that tenant's resources (they
   * own the resource, or hold one of the event's audience permissions on the tenant). The frontend
   * is expected to refetch the actual resource over REST once notified.
   *
   * <p>
   * The gateway also pushes an event of its own on this stream when the session ends, with
   * {@link SseEmitterRegistry#SESSION_RESOURCE_TYPE} as resource type, so that the frontend can
   * switch back to its logged-out state right away instead of waiting for its next REST call to
   * fail.
   * </p>
   *
   * <p>
   * The frontend opens it with an {@code EventSource} (the generated HTTP client cannot consume a
   * never-ending {@code text/event-stream} response).
   * </p>
   *
   * <h4>Access control</h4>
   * <p>
   * Requires the user to be a member of {@code tenant} (with or without roles there).
   * </p>
   *
   * @param tenant the tenant (Keycloak organization) the calling SPA instance is deployed for
   * @param auth the current user
   * @param request the current request, to tie the subscription to its HTTP session
   * @return the event stream
   */
  @PreAuthorize("@tpe.isMember(authentication, #tenant)")
  @GetMapping(path = BASE_PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(implementation = ResourceEvent.class)))
  public SseEmitter subscribeToResourceEvents(
      @PathVariable(name = TENANT_PLACEHOLDER) String tenant, Authentication auth,
      HttpServletRequest request) {
    // A finite timeout is what recycles streams the gateway can no longer see the other end of:
    // the browser's EventSource reconnects on its own when the response ends, so nothing is lost
    // and no heartbeat is needed.
    final var emitter = new SseEmitter(properties.getTimeout().toMillis());
    // getSession(false): an HttpSession method parameter would create the session instead of
    // reading it. A user authenticated with oauth2Login always has one by the time we get here.
    final var session = request.getSession(false);
    if (session == null) {
      throw new IllegalStateException("No HTTP session to subscribe to the events of " + tenant);
    }
    final var permissions = auth.getPrincipal() instanceof HururaaAuthentication user
        ? user.getPermissionsByTenant().getOrDefault(tenant, Set.of())
        : Set.<String>of();
    if (registry.register(session.getId(), tenant, auth.getName(), permissions, emitter)) {
      log.debug("{} subscribed to the events of {}", auth.getName(), tenant);
    }
    // When the registry refused it, the emitter it hands back is already completed with an error:
    // returning it lets the container close the response and the browser retry later.
    return emitter;
  }

}
