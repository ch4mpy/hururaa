import { DestroyRef, Injectable, InjectionToken, effect, inject } from '@angular/core';
import { BASE_PATH, ResourceEvent, ResourceEventEventTypeEnum } from '@api/gateway';
import { Observable, Subject, filter } from 'rxjs';
import { UserService } from './user.service';

/**
 * `ResourceEvent.resourceType` values relayed on the streams: those published by `hururaa-api`
 * (mirroring its `DirectionEvents` constants), plus `SESSION`, which the gateway emits by itself
 * when the HTTP session ends (see its `SseEmitterRegistry.SESSION_RESOURCE_TYPE`).
 */
export const ResourceTypes = {
  APPLICATION: 'application',
  DIRECTION: 'direction',
  GROUP: 'group',
  SESSION: 'session',
} as const;

export { ResourceEventEventTypeEnum as EventType };

/**
 * The subset of `EventSource` this app relies on, injectable so that the stream can be faked in
 * unit tests (jsdom has no `EventSource`).
 */
export type EventStream = Pick<EventSource, 'onmessage' | 'onerror' | 'close'>;

export const EVENT_STREAM_FACTORY = new InjectionToken<(url: string) => EventStream>(
  'EVENT_STREAM_FACTORY',
  {
    providedIn: 'root',
    // same origin as the gateway, still: the session cookie must go with the request
    factory: () => (url) => new EventSource(url, { withCredentials: true }),
  },
);

/**
 * The gateway's notification streams, one per direction the user is a member of: `hururaa-api`
 * addresses its events to every member of the direction concerned, and views listening to them
 * refetch the actual resource over REST (with the user's own access rights).
 *
 * Opened with an `EventSource` rather than the generated `GatewayApi` method: `HttpClient` buffers
 * a response until it completes, which a `text/event-stream` never does. The streams are opened
 * once the user is known to be authenticated and closed when they are not anymore (the gateway
 * answers `401` to anonymous subscriptions, which an `EventSource` does not recover from).
 */
@Injectable({ providedIn: 'root' })
export class ResourceEventsService {
  private readonly basePath = inject(BASE_PATH);
  private readonly user = inject(UserService);
  private readonly openStream = inject(EVENT_STREAM_FACTORY);

  private readonly subject = new Subject<ResourceEvent>();
  private readonly streams = new Map<string, EventStream>();

  /** Every event relayed, whatever its direction. */
  readonly events$: Observable<ResourceEvent> = this.subject.asObservable();

  constructor() {
    effect(() => {
      const directions = this.user.isAuthenticated() ? this.user.directions() : [];
      for (const direction of [...this.streams.keys()]) {
        if (!directions.includes(direction)) {
          this.disconnect(direction);
        }
      }
      for (const direction of directions) {
        this.connect(direction);
      }
    });
    inject(DestroyRef).onDestroy(() => {
      for (const direction of [...this.streams.keys()]) {
        this.disconnect(direction);
      }
    });
  }

  /** The events about resources of the given type (see `ResourceTypes`). */
  of(resourceType: string): Observable<ResourceEvent> {
    return this.events$.pipe(filter((event) => event.resourceType === resourceType));
  }

  private connect(direction: string): void {
    if (this.streams.has(direction)) {
      return;
    }
    const stream = this.openStream(`${this.basePath}/bff/events/${encodeURIComponent(direction)}`);
    stream.onmessage = (message: MessageEvent<string>) => {
      const event = JSON.parse(message.data) as ResourceEvent;
      // the gateway only relays this direction's events, but never trust what comes over the wire
      if (event.tenant === direction) {
        this.subject.next(event);
      }
    };
    // an EventSource reconnects by itself after a network failure: nothing more to do here
    stream.onerror = () => console.warn(`Resource events stream of ${direction} interrupted`);
    this.streams.set(direction, stream);
  }

  private disconnect(direction: string): void {
    this.streams.get(direction)?.close();
    this.streams.delete(direction);
  }
}
