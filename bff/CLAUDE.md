# Conventions Spring Boot — BFF (OAuth2 Client)

Spring Boot 4.0.6 / Java 21, OAuth2 Client, Spring Session JDBC. Le BFF est le seul point d'entrée HTTP entre Angular et le reste du système. Voir le `CLAUDE.md` racine pour la stack complète et les règles de sécurité transversales.

## Rôle du BFF

- Maintient la session utilisateur côté serveur (Spring Session JDBC)
- Échange le code OIDC contre des tokens et les stocke en session — Angular ne voit jamais les tokens
- Expose `/bff/api/**` en proxy vers l'API en ajoutant le `Bearer` token
- Expose `/bff/user-info` pour que Angular connaisse l'identité courante

---

## Proxy vers l'API

Toute nouvelle ressource API accessible depuis Angular passe par `ApiProxyController` — pas besoin de créer un controller BFF dédié pour chaque endpoint. Le wildcard `/**` proxifie tout sous `/bff/api/` vers l'API.

Le proxy utilise `RestClient` (synchrone, contexte Servlet) avec un intercepteur OAuth2 qui injecte le Bearer token depuis la session. Ne pas utiliser `WebClient` avec `.block()` dans un contexte Servlet.

Ne jamais créer de controller BFF qui duplique la logique métier de l'API.

---

## Exposer des données utilisateur

Si une nouvelle information sur l'utilisateur connecté doit être exposée à Angular :

```java
@GetMapping("/my-info")
public ResponseEntity<Map<String, Object>> myInfo(@AuthenticationPrincipal OidcUser user) {
    if (user == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(Map.of("key", user.getClaim("key")));
}
```

Les rôles Keycloak sont dans le claim `realm_access.roles`, pas dans un claim top-level `roles` :

```java
Map<String, Object> realmAccess = user.getClaim("realm_access");
@SuppressWarnings("unchecked")
List<String> roles = (realmAccess != null)
    ? (List<String>) realmAccess.get("roles")
    : List.of();
```

**Règle absolue** : Ne jamais retourner `access_token`, `refresh_token`, ni aucun token dans une réponse HTTP vers le browser.

---

## Headers hop-by-hop dans le proxy

Lors du retour d'une réponse proxifiée, filtrer les headers hop-by-hop (RFC 7230) pour éviter les doublons que nginx rejette avec 502 :

```java
private static final List<String> HOP_BY_HOP = List.of(
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "te", "trailers", "transfer-encoding", "upgrade"
);
```

---

## Configuration OIDC

Les endpoints Keycloak sont configurés en deux URL distinctes dans `application.yml` :

| Paramètre | Valeur | Usage |
|---|---|---|
| `KEYCLOAK_INTERNAL_URI` | `http://keycloak:8080` | token, jwks, userinfo (backchannel BFF→KC) |
| `KEYCLOAK_PUBLIC_URI` | `https://localhost:8443` | authorization-uri, redirect-uri (browser→KC via nginx) |

Ne jamais mélanger les deux. Les URL internes ne doivent jamais être exposées au browser.

```yaml
# application.yml — structure attendue
spring:
  security:
    oauth2:
      client:
        provider:
          keycloak:
            # Endpoints internes : BFF → Keycloak sur backend-net (token, jwks, userinfo)
            token-uri: ${KEYCLOAK_INTERNAL_URI}/realms/my-realm/protocol/openid-connect/token
            jwk-set-uri: ${KEYCLOAK_INTERNAL_URI}/realms/my-realm/protocol/openid-connect/certs
            user-info-uri: ${KEYCLOAK_INTERNAL_URI}/realms/my-realm/protocol/openid-connect/userinfo
            user-name-attribute: preferred_username
            # URL publique : browser → Keycloak via Nginx
            authorization-uri: ${KEYCLOAK_PUBLIC_URI}/realms/my-realm/protocol/openid-connect/auth
        registration:
          keycloak:
            client-id: bff-client
            client-secret: ${BFF_CLIENT_SECRET}
            scope: openid,profile,email,roles
            # {baseUrl} est résolu depuis les headers X-Forwarded-* de Nginx
            # Ne jamais utiliser ${KEYCLOAK_PUBLIC_URI}/... ici — cela ignore forward-headers-strategy
            redirect-uri: "{baseUrl}/login/oauth2/code/keycloak"
```

---

## Sécurité Spring Security (BFF)

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2Login(oauth2 -> oauth2
            .authorizationEndpoint(endpoint -> endpoint
                .authorizationRequestResolver(pkceResolver())
            )
            // Chemin server-relatif — fonctionne quel que soit le hostname/port de déploiement
            .defaultSuccessUrl("/dashboard", false)
        )
        // Retourne 401 pour les appels AJAX non authentifiés (pas de redirect HTML vers Keycloak)
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
        )
        .logout(logout -> logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/")
            .invalidateHttpSession(true)
            .deleteCookies("SESSION")
        )
        .oidcLogout(oidc -> oidc.backChannel(bc -> {}));

    return http.build();
}

// PKCE S256 via DefaultOAuth2AuthorizationRequestResolver
private OAuth2AuthorizationRequestResolver pkceResolver() {
    DefaultOAuth2AuthorizationRequestResolver resolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, "/oauth2/authorization"
        );
    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    return resolver;
}
```

**Règles** :
- PKCE S256 obligatoire via `DefaultOAuth2AuthorizationRequestResolver` + `withPkce()`.
- Cookie XSRF-TOKEN non-HttpOnly pour que Angular puisse le lire.
- Ne jamais désactiver CSRF côté BFF (contrairement à l'API qui est stateless).
- `HttpStatusEntryPoint(401)` à la place de la redirection HTML par défaut — les appels AJAX Angular reçoivent un 401, pas un 302 vers Keycloak.
- `defaultSuccessUrl` et `logoutSuccessUrl` en chemin server-relatif (`/dashboard`, `/`) — ne jamais hardcoder `https://hostname:port`.

---

## Session

Spring Session JDBC stocke la session en base. La table `SPRING_SESSION` est créée automatiquement au premier démarrage si `spring.session.jdbc.initialize-schema=always` (dev) ou via un script Flyway dédié (prod).

```yaml
spring:
  session:
    store-type: jdbc
    jdbc:
      initialize-schema: always   # dev uniquement
  datasource:
    url: jdbc:postgresql://postgres:5432/bff_db
```

---

## Logout

Le logout doit invalider la session locale **et** déclencher un backchannel logout vers Keycloak (RP-Initiated Logout) :

```java
@PostMapping("/logout")
public ResponseEntity<Void> logout(HttpServletRequest request,
                                    @AuthenticationPrincipal OidcUser user) {
    // invalidation session locale
    request.getSession().invalidate();
    SecurityContextHolder.clearContext();
    // redirection vers Keycloak end_session_endpoint gérée par Spring
    return ResponseEntity.noContent().build();
}
```

Préférer la configuration de `OidcClientInitiatedLogoutSuccessHandler` dans Spring Security pour le logout OIDC complet.

---

## Tests

Les tests BFF vérifient principalement le routage et la sécurité. Utiliser `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`. Pour les endpoints OIDC, utiliser `oidcLogin()` de `spring-security-test` (pas `@WithMockUser` — le principal serait `UserDetails`, pas `OidcUser`).

Les requêtes non authentifiées retournent **401** (et non 3xx) grâce à `HttpStatusEntryPoint`.

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BffSecurityTest {

    @Autowired MockMvc mockMvc;

    @Test
    void userInfo_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/bff/user-info"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void userInfo_authenticated_returnsUserInfo() throws Exception {
        mockMvc.perform(get("/bff/user-info")
                .with(oidcLogin()
                    .idToken(token -> token
                        .subject("user-123")
                        .claim("realm_access", Map.of("roles", List.of("USER")))
                    )
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sub").value("user-123"))
            .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void protectedProxy_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/bff/api/my-resource"))
            .andExpect(status().isUnauthorized());
    }
}
```
