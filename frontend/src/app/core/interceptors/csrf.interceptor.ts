import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';

const MUTATING_METHODS = new Set(['POST', 'PUT', 'DELETE', 'PATCH']);

/**
 * Lit le cookie XSRF-TOKEN posé par le BFF et l'ajoute dans le header
 * X-XSRF-TOKEN sur toutes les requêtes mutantes (POST, PUT, DELETE, PATCH).
 * Protection CSRF double-submit cookie.
 */
export const csrfInterceptor: HttpInterceptorFn = (req, next) => {
  if (!MUTATING_METHODS.has(req.method)) {
    return next(req);
  }

  const token = getCookieValue('XSRF-TOKEN');
  if (!token) {
    return next(req);
  }

  const cloned: HttpRequest<unknown> = req.clone({
    setHeaders: { 'X-XSRF-TOKEN': token }
  });

  return next(cloned);
};

function getCookieValue(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : null;
}
