import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ResourceEventEventTypeEnum, provideApi } from '@api/gateway';
import { ErrorBannerService } from './error-banner.service';
import { ResourceTypes } from './resource-events.service';
import { FakeEventStream, provideFakeEventStreams, resourceEvent } from './resource-events.testing';
import { SessionEndService } from './session-end.service';
import { UserService } from './user.service';

describe('SessionEndService', () => {
  let http: HttpTestingController;
  let streams: FakeEventStream[];

  beforeEach(() => {
    streams = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideApi('/gateway'),
        provideFakeEventStreams(streams),
      ],
    });
    TestBed.inject(SessionEndService);
    http = TestBed.inject(HttpTestingController);

    // authenticated: /me answers with a subject, which opens the stream
    http.expectOne('/gateway/me').flush({ sub: 'u1', directions: ['dpam'] });
    TestBed.tick();
  });

  afterEach(() => http.verify());

  it('switches back to the logged-out state, closes the stream and explains why', () => {
    const user = TestBed.inject(UserService);
    expect(user.isAuthenticated()).toBe(true);

    streams[0].emit(
      resourceEvent({
        resourceType: ResourceTypes.SESSION,
        resourceId: 'u1',
        resourceOwner: 'u1',
        eventType: ResourceEventEventTypeEnum.delete,
      }),
    );

    // the user is refetched rather than assumed anonymous: the gateway is the authority
    http.expectOne('/gateway/me').flush({ directions: [] });
    TestBed.tick();

    expect(user.isAuthenticated()).toBe(false);
    expect(streams[0].closed).toBe(true);
    expect(TestBed.inject(ErrorBannerService).message()).toBe(
      'Votre session a pris fin, veuillez vous reconnecter',
    );
  });
});
