import { Provider } from '@angular/core';
import { ResourceEvent, ResourceEventEventTypeEnum } from '@api/gateway';
import { EVENT_STREAM_FACTORY, EventStream } from './resource-events.service';

/**
 * A stand-in for the browser's `EventSource` (which jsdom does not provide): records the URL it
 * was opened with and lets tests push events as if the gateway had sent them.
 */
export class FakeEventStream implements EventStream {
  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  closed = false;

  constructor(readonly url: string) {}

  close(): void {
    this.closed = true;
  }

  /** Delivers an event the way the gateway would (as the JSON `data` of an SSE message). */
  emit(event: ResourceEvent): void {
    this.onmessage?.(new MessageEvent('message', { data: JSON.stringify(event) }));
  }
}

/**
 * Provides `ResourceEventsService` with fake streams, collected in `streams` in the order they
 * were opened (one per direction of an authenticated `/me` answer).
 */
export function provideFakeEventStreams(streams: FakeEventStream[]): Provider {
  return {
    provide: EVENT_STREAM_FACTORY,
    useValue: (url: string) => {
      const stream = new FakeEventStream(url);
      streams.push(stream);
      return stream;
    },
  };
}

/** A `dpam` event with the given fields, the others defaulted. */
export function resourceEvent(event: Partial<ResourceEvent>): ResourceEvent {
  return {
    tenant: 'dpam',
    resourceType: 'application',
    resourceId: '1',
    audience: [],
    eventType: ResourceEventEventTypeEnum.update,
    occurredAt: '2026-09-15T10:00:00Z',
    ...event,
  };
}
