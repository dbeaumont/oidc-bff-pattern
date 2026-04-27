import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Vérifie si l'utilisateur est authentifié en appelant /bff/user-info.
 * Si non authentifié (401), redirige vers Keycloak via le BFF.
 * Aucun token n'est manipulé côté client.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);

  return authService.getUserInfo().pipe(
    map(user => {
      if (user) return true;
      authService.login();
      return false;
    })
  );
};
