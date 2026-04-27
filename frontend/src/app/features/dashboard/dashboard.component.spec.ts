import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { AuthService, UserInfo } from '../../core/services/auth.service';

const mockUser: UserInfo = {
  sub: 'user-1',
  email: 'john@example.com',
  firstName: 'John',
  lastName: 'Doe',
  username: 'john.doe',
  roles: ['USER'],
};

describe('DashboardComponent', () => {
  let httpMock: HttpTestingController;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['getUserInfo', 'logout']);
    authServiceSpy.getUserInfo.and.returnValue(of(mockUser));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('charge les items au démarrage et désactive le loading', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne('/bff/api/items');
    req.flush([{ id: 1, name: 'Test', description: 'Desc' }]);
    fixture.detectChanges();

    expect(fixture.componentInstance.items()).toHaveSize(1);
    expect(fixture.componentInstance.loading()).toBeFalse();
    expect(fixture.componentInstance.error()).toBeNull();
  });

  it("affiche le signal error en cas d'échec HTTP", () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/items').error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).not.toBeNull();
    expect(fixture.componentInstance.loading()).toBeFalse();
    expect(fixture.componentInstance.items()).toHaveSize(0);
  });

  it('expose les informations utilisateur via le signal user', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    httpMock.expectOne('/bff/api/items').flush([]);

    expect(fixture.componentInstance.user()).toEqual(mockUser);
  });
});
