package pf.hururaa.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pf.hururaa.commons.events.ResourceEvent;
import pf.hururaa.commons.events.ResourceEvent.EventType;

class SseEmitterRegistryTest {

  private static final String TEMPLATES_READ = "hururaa.templates.read";
  private static final String FORMS_READ_ANY = "hururaa.forms.read-any";

  private final SseProperties properties = new SseProperties();

  private final SseEmitterRegistry registry = new SseEmitterRegistry(properties);

  private static ResourceEvent event(String tenant, @Nullable String owner, String... audience) {
    return new ResourceEvent(
        tenant, "form", "10", "1", owner, List.of(audience), EventType.UPDATE, Instant.now());
  }

  private SseEmitter subscribe(String sessionId, String tenant, String subject, String... permissions) {
    final var emitter = mock(SseEmitter.class);
    registry.register(sessionId, tenant, subject, Set.of(permissions), emitter);
    return emitter;
  }

  @Test
  void givenSubscriberHoldsAnAudiencePermissionOnTenant_whenRelay_thenSent() throws IOException {
    final var emitter = subscribe("session-1", "tenant3", "manager3", TEMPLATES_READ, FORMS_READ_ANY);
    final var event = event("tenant3", "someone-else", FORMS_READ_ANY);

    registry.relay(event);

    verify(emitter).send(event);
  }

  @Test
  void givenSubscriberOwnsResourceWithoutAudiencePermission_whenRelay_thenSent() throws IOException {
    final var emitter = subscribe("session-1", "tenant3", "author3", "hururaa.forms.create");
    final var event = event("tenant3", "author3", FORMS_READ_ANY);

    registry.relay(event);

    verify(emitter).send(event);
  }

  @Test
  void givenSubscriberNeitherOwnsNorHoldsAudiencePermission_whenRelay_thenNotSent() throws IOException {
    final var emitter = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);

    registry.relay(event("tenant3", "author3", FORMS_READ_ANY));

    verify(emitter, never()).send(any(Object.class));
  }

  @Test
  void givenSubscriptionForAnotherTenant_whenRelay_thenNotSentEvenWithSamePermissionOrAsOwner()
      throws IOException {
    final var emitter = subscribe("session-1", "tenant1", "author3", FORMS_READ_ANY);

    registry.relay(event("tenant3", "author3", FORMS_READ_ANY));

    verify(emitter, never()).send(any(Object.class));
  }

  @Test
  void givenAllMembersAudience_whenRelay_thenSentToEverySubscriberOfTheTenantOnly()
      throws IOException {
    final var withoutPermission = subscribe("session-1", "dpam", "agent");
    final var withPermission = subscribe("session-2", "dpam", "manager", TEMPLATES_READ);
    final var otherTenant = subscribe("session-3", "dsi", "someone");
    final var event = event("dpam", null, ResourceEvent.ALL_MEMBERS);

    registry.relay(event);

    verify(withoutPermission).send(event);
    verify(withPermission).send(event);
    verify(otherTenant, never()).send(any(Object.class));
  }

  @Test
  void givenNoOwner_whenRelay_thenOnlyAudienceCounts() throws IOException {
    final var reader = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);
    final var other = subscribe("session-2", "tenant3", "author3", "hururaa.forms.create");
    final var event = event("tenant3", null, TEMPLATES_READ);

    registry.relay(event);

    verify(reader).send(event);
    verify(other, never()).send(any(Object.class));
  }

  @Test
  void givenSendFails_whenRelay_thenSubscriptionIsDropped() throws IOException {
    final var emitter = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);
    doThrow(new IOException("gone")).when(emitter).send(any(Object.class));

    registry.relay(event("tenant3", null, TEMPLATES_READ));

    assertThat(registry.size()).isZero();
  }

  @Test
  void whenEndSession_thenTheUserIsSentADeleteEventAboutThemselvesAndTheStreamIsClosed()
      throws IOException {
    final var emitter = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);
    final var captor = ArgumentCaptor.forClass(ResourceEvent.class);

    registry.endSession("session-1");

    verify(emitter).send(captor.capture());
    assertThat(captor.getValue())
        .returns("tenant3", ResourceEvent::tenant)
        .returns(SseEmitterRegistry.SESSION_RESOURCE_TYPE, ResourceEvent::resourceType)
        .returns("worker3", ResourceEvent::resourceId)
        .returns("worker3", ResourceEvent::resourceOwner)
        .returns(null, ResourceEvent::parentId)
        .returns(EventType.DELETE, ResourceEvent::eventType)
        .extracting(ResourceEvent::audience, InstanceOfAssertFactories.LIST)
        .isEmpty();
    verify(emitter).complete();
    assertThat(registry.size()).isZero();
  }

  @Test
  void givenTheSameUserIsAlsoConnectedFromAnotherSession_whenEndSession_thenItIsLeftAlone()
      throws IOException {
    final var ended = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);
    final var other = subscribe("session-2", "tenant3", "worker3", TEMPLATES_READ);

    registry.endSession("session-1");

    verify(ended).send(any(Object.class));
    verify(other, never()).send(any(Object.class));
    verify(other, never()).complete();
    assertThat(registry.size()).isOne();
  }

  @Test
  void givenSeveralTenantsInTheSameSession_whenEndSession_thenAllAreNotified() throws IOException {
    final var first = subscribe("session-1", "tenant1", "manager3", TEMPLATES_READ);
    final var second = subscribe("session-1", "tenant3", "manager3", TEMPLATES_READ);

    registry.endSession("session-1");

    verify(first).complete();
    verify(second).complete();
    assertThat(registry.size()).isZero();
  }

  @Test
  void givenSendFails_whenEndSession_thenSubscriptionIsDroppedWithoutThrowing() throws IOException {
    final var emitter = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);
    doThrow(new IOException("gone")).when(emitter).send(any(Object.class));

    registry.endSession("session-1");

    assertThat(registry.size()).isZero();
  }

  @Test
  void givenEmitterAlreadyCompleted_whenEndSession_thenSubscriptionIsDroppedWithoutThrowing()
      throws IOException {
    final var emitter = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);
    doThrow(new IllegalStateException("already completed")).when(emitter).complete();

    registry.endSession("session-1");

    assertThat(registry.size()).isZero();
  }

  @Test
  void givenUnknownSession_whenEndSession_thenNothingHappens() throws IOException {
    final var emitter = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);

    registry.endSession("session-2");

    verify(emitter, never()).send(any(Object.class));
    verify(emitter, never()).complete();
    assertThat(registry.size()).isOne();
  }

  @Test
  void givenSessionAlreadyAtItsLimit_whenRegister_thenTheOldestStreamIsRecycled() throws IOException {
    properties.setMaxSubscriptionsPerSession(2);
    final var first = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);
    final var second = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);

    final var third = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);

    verify(first).complete();
    verify(second, never()).complete();
    assertThat(registry.size()).isEqualTo(2);

    final var event = event("tenant3", "worker3", TEMPLATES_READ);
    registry.relay(event);
    verify(first, never()).send(event);
    verify(third).send(event);
  }

  @Test
  void givenAnotherSessionIsAtItsLimit_whenRegister_thenThatOtherSessionIsLeftAlone() {
    properties.setMaxSubscriptionsPerSession(1);
    final var other = subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);

    subscribe("session-2", "tenant3", "manager3", TEMPLATES_READ);

    verify(other, never()).complete();
    assertThat(registry.size()).isEqualTo(2);
  }

  @Test
  void givenTheGatewayIsAtItsGlobalLimit_whenRegister_thenTheSubscriptionIsRefused() {
    properties.setMaxSubscriptions(1);
    subscribe("session-1", "tenant3", "worker3", TEMPLATES_READ);
    final var refused = mock(SseEmitter.class);

    final var accepted =
        registry.register("session-2", "tenant3", "manager3", Set.of(TEMPLATES_READ), refused);

    assertThat(accepted).isFalse();
    assertThat(registry.size()).isEqualTo(1);
    verify(refused).completeWithError(any());
  }

  @Test
  void givenRoomIsAvailable_whenRegister_thenTheSubscriptionIsAccepted() {
    final var emitter = mock(SseEmitter.class);

    final var accepted =
        registry.register("session-1", "tenant3", "worker3", Set.of(TEMPLATES_READ), emitter);

    assertThat(accepted).isTrue();
    assertThat(registry.size()).isEqualTo(1);
  }
}
