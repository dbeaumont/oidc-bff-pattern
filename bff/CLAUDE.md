# Conventions Spring Boot — BFF (OAuth2 Client)

Spring Boot 3.4.1 / Java 21, OAuth2 Client, Spring Session JDBC. Le BFF est le seul point d'entrée HTTP entre Angular et le reste du système. Voir le `CLAUDE.md` racine pour la stack complète et les règles de sécurité transversales.

## Rôle du BFF

- Maintient la session utilisateur côté serveur (Spring Session JDBC)
- Échange le code OIDC contre des tokens et les stocke en session — Angular ne voit jamais les tokens
- Expose `/bff/api/**` en proxy vers l'API en ajoutant le `Bearer` token
- Expose `/bff/user-info` pour que Angular connaisse l'identité courante

---

## Proxy vers l'API

Toute nouvelle ressource API accessible depuis Angular passe par `ApiProxyController` — pas besoin de créer un controller BFF dédié pour chaque endpoint. Le wildcard `/**` proxifie tout sous `/bff/api/` vers l'API.

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
            issuer-uri: ${KEYCLOAK_INTERNAL_URI}/realms/my-realm
            authorization-uri: ${KEYCLOAK_PUBLIC_URI}/realms/my-realm/protocol/openid-connect/auth
        registration:
          keycloak:
            client-id: bff
            client-secret: ${KEYCLOAK_CLIENT_SECRET}
            scope: openid, profile, email
```

---

## Sécurité Spring Security (BFF)

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/bff/user-info", "/bff/login", "/bff/logout").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2Login(login -> login
            .authorizationEndpoint(e ->
                e.authorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce())
            )
        )
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
        )
        .build();
}
```

**Règles** :
- PKCE S256 obligatoire via `OAuth2AuthorizationRequestCustomizers.withPkce()`.
- Cookie XSRF-TOKEN non-HttpOnly pour que Angular puisse le lire.
- Ne jamais désactiver CSRF côté BFF (contrairement à l'API qui est stateless).

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

Les tests BFF vérifient principalement le routage et la sécurité. Utiliser `@SpringBootTest` + `@AutoConfigureMockMvc` avec un Keycloak mocké (WireMock) ou `@WithMockUser` pour les tests unitaires de sécurité.

```java
@SpringBootTest
@AutoConfigureMockMvc
class BffSecurityTest {

    @Autowired MockMvc mockMvc;

    @Test
    void userInfo_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/bff/user-info"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void userInfo_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/bff/user-info"))
            .andExpect(status().isOk());
    }

    @Test
    void protectedProxy_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/bff/api/my-resource"))
            .andExpect(status().is3xxRedirection());
    }
}
```
