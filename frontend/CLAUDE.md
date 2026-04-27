# Conventions Angular

Angular 19, composants standalone uniquement. Voir le `CLAUDE.md` racine pour la stack complète et les règles de sécurité transversales.

## Règles générales

- **Toujours** utiliser des composants standalone (`standalone: true`). Aucun `NgModule`.
- Injection via `inject()` dans le corps de la classe, jamais via constructeur.
- Les services sont `providedIn: 'root'` sauf besoin explicite de scope.
- Pas de `async/await` dans les composants : utiliser les `Observable` RxJS avec `.subscribe()` dans `ngOnInit`, stocker le résultat dans des `signal()`, et lire les signals dans le template via `signal()`.
- Le pipe `async` est déconseillé : il ré-exécute l'Observable à chaque appel (risque de requêtes multiples). Préférer les signals.
- Aucun token OAuth2 ni donnée d'authentification ne transite côté client. L'identité vient uniquement de `/bff/user-info`.

---

## Composant

```typescript
import { Component, OnInit, inject, signal } from '@angular/core';
import { MyDataService, MyData } from '../../core/services/my-data.service';

@Component({
  selector: 'app-my-feature',
  standalone: true,
  imports: [],   // CommonModule inutile : @if et @for font partie du compilateur Angular 17+
  templateUrl: './my-feature.component.html',
  styleUrl: './my-feature.component.scss'
})
export class MyFeatureComponent implements OnInit {
  private readonly myDataService = inject(MyDataService);

  readonly items = signal<MyData[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.myDataService.getAll().subscribe({
      next: data => { this.items.set(data); this.loading.set(false); },
      error: () => { this.error.set('Impossible de charger les données.'); this.loading.set(false); }
    });
  }
}
```

**Règles** :
- `readonly` sur toutes les dépendances injectées et sur les signals (la référence est constante, seule la valeur change via `.set()`).
- Déclarer explicitement l'interface du lifecycle hook (`implements OnInit`, `implements OnDestroy`, etc.).
- Template inline uniquement pour les composants < 5 lignes, sinon `templateUrl`.
- Pas de logique métier dans le template — déléguer à des méthodes ou des `computed()`.

---

## Route avec lazy loading

```typescript
// app.routes.ts
export const routes: Routes = [
  {
    path: 'my-feature',
    loadComponent: () =>
      import('./features/my-feature/my-feature.component').then(m => m.MyFeatureComponent),
    canActivate: [authGuard]
  }
];
```

Toutes les routes protégées ont `canActivate: [authGuard]`.

---

## Guard fonctionnel

```typescript
import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../core/auth.service';

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
```

Guards sous forme de fonctions (`CanActivateFn`), jamais de classes implémentant `CanActivate`.

---

## Intercepteur HTTP fonctionnel

```typescript
import { HttpInterceptorFn } from '@angular/common/http';

export const myInterceptor: HttpInterceptorFn = (req, next) => {
  const modified = req.clone({ setHeaders: { 'X-Custom': 'value' } });
  return next(modified);
};
```

Enregistrement dans `app.config.ts` : `provideHttpClient(withInterceptors([myInterceptor]))`.

L'intercepteur CSRF existant (`csrfInterceptor`) lit le cookie `XSRF-TOKEN` et envoie `X-XSRF-TOKEN` sur POST/PUT/DELETE/PATCH — ne pas le dupliquer.

---

## Service

```typescript
@Injectable({ providedIn: 'root' })
export class MyService {
  private readonly http = inject(HttpClient);

  getAll(): Observable<MyData[]> {
    return this.http.get<MyData[]>('/bff/api/my-resource');
  }

  create(payload: Omit<MyData, 'id'>): Observable<MyData> {
    return this.http.post<MyData>('/bff/api/my-resource', payload);
  }
}
```

**Règles** :
- Toutes les URLs sont relatives (commencent par `/bff/`), jamais de base URL absolue.
- Les méthodes retournent des `Observable`, jamais de `Promise`.
- Pas de `catchError` dans les services : laisser les composants gérer les erreurs.

---

## Vérification des rôles dans le template

Stocker l'utilisateur dans un signal via `subscribe()` dans `ngOnInit` — ne pas appeler `getUserInfo()` directement dans le template (déclencherait une nouvelle requête HTTP à chaque détection de changement).

```typescript
// Dans le composant
readonly user = signal<UserInfo | null>(null);

ngOnInit(): void {
  inject(AuthService).getUserInfo().subscribe(u => this.user.set(u));
}
```

```html
@if (user()?.roles.includes('ADMIN')) {
  <button (click)="delete(item.id)">Supprimer</button>
}
```

---

## Tests

Placer les specs dans `{component}.spec.ts` au même niveau que le fichier testé. Utiliser `HttpTestingController` pour vérifier les appels HTTP sans réseau réel.

```typescript
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { MyFeatureComponent } from './my-feature.component';

describe('MyFeatureComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MyFeatureComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('charge les données au démarrage', () => {
    const fixture = TestBed.createComponent(MyFeatureComponent);
    fixture.detectChanges();

    const req = httpMock.expectOne('/bff/api/my-resource');
    req.flush([{ id: 1, label: 'Test' }]);

    expect(fixture.componentInstance.items()).toHaveLength(1);
    expect(fixture.componentInstance.loading()).toBe(false);
  });

  it('positionne error en cas d\'échec', () => {
    const fixture = TestBed.createComponent(MyFeatureComponent);
    fixture.detectChanges();

    httpMock.expectOne('/bff/api/my-resource').error(new ProgressEvent('error'));

    expect(fixture.componentInstance.error()).not.toBeNull();
    expect(fixture.componentInstance.loading()).toBe(false);
  });
});
```
