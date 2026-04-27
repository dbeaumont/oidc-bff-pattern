import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, of, shareReplay } from 'rxjs';

export interface UserInfo {
  sub: string;
  email: string;
  firstName: string;
  lastName: string;
  username: string;
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly userInfoUrl = '/bff/user-info';

  /** Cache de l'identité — invalide lors du logout */
  private userInfo$: Observable<UserInfo | null> | null = null;

  getUserInfo(): Observable<UserInfo | null> {
    if (!this.userInfo$) {
      this.userInfo$ = this.http.get<UserInfo>(this.userInfoUrl).pipe(
        catchError(() => of(null)),
        shareReplay(1)
      );
    }
    return this.userInfo$;
  }

  login(): void {
    window.location.href = '/bff/oauth2/authorization/keycloak';
  }

  logout(): void {
    this.userInfo$ = null;
    window.location.href = '/bff/logout';
  }
}
