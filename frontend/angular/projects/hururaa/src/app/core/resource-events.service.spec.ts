import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApi } from '@api/gateway';
import { ResourceEvent } from '@api/gateway';
import { ResourceEventsService, ResourceTypes } from './resource-events.service';
import { FakeEventStream, provideFakeEventStreams, resourceEvent } from './resource-events.testing';
import { UserService } from './user.service';

describe('ResourceEventsService', () => {
  let http: HttpTestingController;
  let service: ResourceEventsService;
  let streams: FakeEventStream[];

  beforeEach(() => {
    streams = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi('/gateway'),
        provideFakeEventStreams(streams),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ResourceEventsService);
  });

  afterEach(() => http.verify());

  const answerMe = (body: object) => {
    http.expectOne('/gateway/me').flush(body);
    TestBed.tick();
  };

  it('opens no stream while anonymous', () => {
    answerMe({ directions: [] });

    expect(streams).toHaveLength(0);
  });

  it('opens one stream per direction the user is a member of', () => {
    answerMe({ sub: 'u1', directions: ['dpam', 'dsi'] });

    expect(streams.map((s) => s.url)).toEqual([
      '/gateway/bff/events/dpam',
      '/gateway/bff/events/dsi',
    ]);
  });

  it('relays the events of a stream, and drops those claiming another direction', () => {
    answerMe({ sub: 'u1', directions: ['dpam'] });
    const received: ResourceEvent[] = [];
    service.of(ResourceTypes.APPLICATION).subscribe((event) => received.push(event));

    streams[0].emit(resourceEvent({ resourceId: '3' }));
    streams[0].emit(resourceEvent({ tenant: 'dsi', resourceId: '2' }));
    streams[0].emit(resourceEvent({ resourceType: ResourceTypes.GROUP }));

    expect(received.map((e) => e.resourceId)).toEqual(['3']);
  });

  it('closes the streams once the user is not authenticated anymore', () => {
    answerMe({ sub: 'u1', directions: ['dpam'] });

    TestBed.inject(UserService).refresh();
    answerMe({ directions: [] });

    expect(streams[0].closed).toBe(true);
  });
});
