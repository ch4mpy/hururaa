import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideApi as provideGatewayApi } from '@api/gateway';
import { DelegationsResponse, provideApi as provideHururaaApi } from '@api/hururaa-api';
import { DelegationsService, HururaaRoles } from './delegations.service';

const escales = {
  id: 3,
  clientPrefix: 'escales',
  name: 'Escales',
  direction: 'dpam',
  bffClientId: 'escales-bff',
  apiClientId: 'escales-api',
};

describe('DelegationsService', () => {
  let http: HttpTestingController;
  let service: DelegationsService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideGatewayApi('/gateway'),
        provideHururaaApi('/api'),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(DelegationsService);
  });

  afterEach(() => http.verify());

  const login = (delegations: DelegationsResponse) => {
    http.expectOne('/gateway/me').flush({ sub: 'u1', directions: ['dpam'] });
    TestBed.tick();
    http.expectOne('/api/me/delegations').flush(delegations);
  };

  it('asks nothing to the API while anonymous', () => {
    http.expectOne('/gateway/me').flush({ directions: [] });
    TestBed.tick();

    http.expectNone('/api/me/delegations');
    expect(service.hasAny()).toBe(false);
  });

  it("lets Hurura'a administrators act at every level", () => {
    login({
      hururaaRoles: [HururaaRoles.ADMIN],
      platformOrganization: 'dsi',
      administeredDirections: [],
      managedApplications: [],
    });

    expect(service.isAdmin()).toBe(true);
    expect(service.registrationDirections()).toBeUndefined();
    expect(service.canReadDirection('dpam')).toBe(true);
    expect(service.canEditApplicationsOf('dpam')).toBe(true);
    expect(service.canManageApplication(escales)).toBe(true);
    expect(service.canManageGroup({ direction: 'dpam', applicationId: 9 })).toBe(true);
    expect(service.hasAny()).toBe(true);
  });

  it('tells apart direction administrators and application managers', () => {
    login({
      hururaaRoles: [],
      platformOrganization: 'dsi',
      administeredDirections: ['daf'],
      managedApplications: [escales],
    });

    expect(service.isDirectionAdmin('daf')).toBe(true);
    expect(service.isDirectionAdmin('dpam')).toBe(false);
    expect(service.isApplicationManager(3)).toBe(true);
    expect(service.isManagerInDirection('dpam')).toBe(true);
    expect(service.isManagerInDirection('daf')).toBe(false);
    expect(service.canReadDirection('daf')).toBe(true);
    expect(service.canReadDirection('dpam')).toBe(true);
    expect(service.canReadDirection('dsi')).toBe(false);
    expect(service.isAdmin()).toBe(false);
    expect(service.registrationDirections()).toEqual(['daf']);
  });

  it("lets direction administrators manage their direction's applications only", () => {
    login({
      hururaaRoles: [],
      platformOrganization: 'dsi',
      administeredDirections: ['dpam'],
      managedApplications: [],
    });

    expect(service.canEditApplicationsOf('dpam')).toBe(true);
    expect(service.canManageApplication(escales)).toBe(true);
    expect(service.canEditApplicationsOf('daf')).toBe(false);
    expect(service.canManageGroup({ direction: 'dpam', applicationId: 9 })).toBe(true);
    expect(service.canManageGroup({ direction: 'dpam' })).toBe(true);
    expect(service.canManageGroup({ direction: 'daf', applicationId: 9 })).toBe(false);
  });

  it('lets managers manage the applications they manage, not edit them', () => {
    login({
      hururaaRoles: [],
      platformOrganization: 'dsi',
      administeredDirections: [],
      managedApplications: [escales],
    });

    expect(service.canManageApplication(escales)).toBe(true);
    expect(service.canEditApplicationsOf('dpam')).toBe(false);
    expect(service.canManageGroup({ direction: 'dpam', applicationId: 3 })).toBe(true);
    // another application of the direction, or a group belonging to none
    expect(service.canManageGroup({ direction: 'dpam', applicationId: 9 })).toBe(false);
    expect(service.canManageGroup({ direction: 'dpam' })).toBe(false);
  });
});
