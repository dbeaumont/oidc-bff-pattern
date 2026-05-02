# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Chaque sous-répertoire (`bff/`, `api/`, `frontend/`) contient un `CLAUDE.md` avec les conventions spécifiques à sa couche.

## Stack

| Couche | Technologie | Version |
|---|---|---|
| Frontend | Angular (standalone components) | 19 |
| BFF | Spring Boot — OAuth2 Client + Spring Session JDBC | 4.0.6 / Java 21 |
| API | Spring Boot — OAuth2 Resource Server + Flyway | 4.0.6 / Java 21 |
| Auth | Keycloak OIDC (realm `my-realm`) | 26 |
| Base de données | PostgreSQL | 16 |

---

## Commandes

```bash
# Environnement Docker (keycloak, postgres, nginx)
make up
make down
make logs

# Frontend
cd frontend && npm install
cd frontend && npm start        # http://localhost:4200
cd frontend && npm test
cd frontend && npm run build

# API / BFF (tous les tests)
cd api && ./mvnw test
cd bff && ./mvnw test

# API / BFF (une seule classe ou méthode)
cd api && ./mvnw test -Dtest=ItemControllerTest
cd api && ./mvnw test -Dtest=ItemControllerTest#create_returnsCreatedWithLocation

# Démarrage local (hors Docker)
cd api && ./mvnw spring-boot:run
cd bff && ./mvnw spring-boot:run
```

### Prérequis : fichier `.env`

Le BFF lit `BFF_CLIENT_SECRET` depuis un fichier `.env` à la racine (chargé par Docker Compose). Créer ce fichier avant `make up` :

```bash
echo "BFF_CLIENT_SECRET=your-secret" > .env
```

---

## Architecture réseau

```
Browser
  └── https://localhost:8443 (Nginx)
        ├── /realms/    → keycloak:8080  (flow OIDC browser→KC)
        ├── /bff/       → bff:8081       (session, proxy, user-info)
        └── /           → frontend:80    (SPA Angular)

bff:8081 → api:8082          (proxy interne, Bearer token injecté)
bff:8081 → keycloak:8080     (backchannel : token, JWKS, userinfo)
api:8082 → keycloak:8080     (backchannel : JWKS pour validation JWT)
api:8082 → postgres:5432     (api_db)
bff:8081 → postgres:5432     (bff_db — Spring Session JDBC)
```

L'API (`api:8082`) est sur le réseau `backend-net` uniquement — jamais accessible depuis le browser ni depuis Angular.

---

## Structure du projet

```
.
├── frontend/
│   └── src/app/
│       ├── core/              # services singleton, guards, intercepteurs
│       ├── features/          # un répertoire par fonctionnalité
│       │   └── {feature}/
│       │       ├── {feature}.component.ts
│       │       ├── {feature}.component.html
│       │       └── {feature}.component.scss
│       ├── shared/            # composants et pipes réutilisables
│       ├── app.routes.ts
│       └── app.config.ts
├── api/                       # package racine : com.enterprise.api
│   └── src/main/
│       ├── java/.../
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── entity/
│       │   ├── repository/
│       │   ├── service/
│       │   └── config/
│       └── resources/db/migration/
├── bff/                       # package racine : com.enterprise.bff
│   └── src/main/
│       ├── java/.../
│       │   ├── web/           # UserInfoController
│       │   ├── proxy/         # ApiProxyController (wildcard vers l'API)
│       │   └── config/        # SecurityConfig, WebClientConfig
│       └── resources/
├── keycloak/realm-export.json
├── nginx/nginx.conf
├── docker-compose.yml
└── Makefile
```

---

## Sécurité — règles transversales

| Règle | Détail |
|---|---|
| Tokens côté serveur uniquement | Le BFF stocke `access_token` et `refresh_token` en session JDBC. Angular ne les voit jamais. |
| Cookie SESSION | `HttpOnly; Secure; SameSite=Strict` — inaccessible à JavaScript. |
| Cookie XSRF-TOKEN | Non-HttpOnly — lu par Angular pour le header `X-XSRF-TOKEN` sur les mutations. |
| Rôles Keycloak | Extraits de `realm_access.roles` dans le JWT. Mappés en `ROLE_USER`, `ROLE_ADMIN`. |
| Validation JWT | L'API valide la signature via JWKS interne + l'issuer public (`https://localhost:8443/realms/my-realm`). |
| PKCE S256 | Forcé via `OAuth2AuthorizationRequestCustomizers.withPkce()` dans le BFF. |

---

## Ajouter une nouvelle fonctionnalité — checklist

### Nouvelle ressource côté API

1. Créer le script Flyway `V{n}__add_{resource}.sql`
2. Créer l'entité `@Entity` avec contraintes Bean Validation + constructeur paramétré
3. Créer les DTOs : record `{Resource}Request` et record `{Resource}Response` (avec `from()`)
4. Créer `interface {Resource}Repository extends JpaRepository<{Resource}, Long>`
5. Créer `{Resource}Service` avec logs et `ResponseStatusException` pour les 404
6. Créer `{Resource}Controller` avec `@PreAuthorize` sur chaque endpoint
7. Vérifier que `ddl-auto: validate` passe sans erreur au démarrage

### Nouvelle page Angular

1. Créer le composant standalone dans `src/app/features/{feature}/`
2. Ajouter la route dans `app.routes.ts` avec `loadComponent` et `canActivate: [authGuard]`
3. Appeler uniquement des URLs `/bff/api/...` depuis `HttpClient`
4. Gérer les états `loading`, `error` dans des `signal()` (`readonly`)
5. Vérifier les rôles si la fonctionnalité est réservée à certains profils

### Nouveau rôle Keycloak

1. Ajouter le rôle dans `keycloak/realm-export.json` (tableau `roles.realm`)
2. L'assigner aux utilisateurs concernés dans le même fichier
3. Relancer Keycloak : `docker compose rm -sf keycloak && make up`
4. Utiliser `@PreAuthorize("hasRole('MY_ROLE')")` côté API
5. Lire `user.roles.includes('MY_ROLE')` côté Angular

---

## Ce qu'il ne faut jamais faire

- `NgModule` — cette base de code est 100% standalone Angular
- `@Autowired` sur des champs Java
- `ddl-auto: create` ou `update` en dehors d'un environnement de test
- Retourner un token OAuth2 dans une réponse HTTP vers le browser
- Appeler `http://keycloak:8080` depuis Angular (réseau Docker non accessible)
- Appeler l'API directement depuis Angular sans passer par `/bff/api/`
- Modifier un script Flyway déjà appliqué
- Passer `KC_HOSTNAME` + `KC_HOSTNAME_PORT` dans Keycloak 26 : utiliser `KC_HOSTNAME: "https://host:port"` (URL complète, format v2)
- Exposer une entité JPA directement en request/response body dans un controller
- Forcer un id sur une entité non gérée par JPA dans un `update`
- Signals Angular sans `readonly` sur la référence
- Créer un controller BFF dédié par endpoint API — le proxy wildcard `ApiProxyController` couvre tout `/bff/api/**`
