# Conventions Spring Boot — API (Resource Server)

Spring Boot 3.4.1 / Java 21, OAuth2 Resource Server, Flyway, PostgreSQL. Voir le `CLAUDE.md` racine pour la stack complète et les règles de sécurité transversales.

## Règles générales

- Injection par constructeur uniquement (pas de `@Autowired` sur champs).
- Stateless : `SessionCreationPolicy.STATELESS`, CSRF désactivé.
- Autorisation au niveau méthode avec `@PreAuthorize`.
- Les rôles sont préfixés `ROLE_` par Spring Security — utiliser `hasRole('USER')` (sans préfixe) ou `hasAuthority('ROLE_USER')`.
- Schéma géré par Flyway (`ddl-auto: validate`). Ne jamais utiliser `create` ou `update` en dehors du dev local.
- **Ne jamais exposer une entité JPA directement en request ou response body** : toujours passer par des DTOs.

---

## DTOs

Utiliser des **records Java** (immuables, getters générés) pour les DTOs request et response. Placer dans le package `dto/`.

```java
// MyResourceRequest.java
public record MyResourceRequest(
    @NotBlank String name,
    String description
) {}

// MyResourceResponse.java
public record MyResourceResponse(Long id, String name, String description) {
    public static MyResourceResponse from(MyResource entity) {
        return new MyResourceResponse(entity.getId(), entity.getName(), entity.getDescription());
    }
}
```

---

## Controller

```java
@RestController
@RequestMapping("/my-resource")
public class MyResourceController {

    private final MyResourceRepository repository;

    public MyResourceController(MyResourceRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<MyResourceResponse> findAll() {
        return repository.findAll().stream().map(MyResourceResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MyResourceResponse> findById(@PathVariable Long id) {
        return repository.findById(id)
            .map(MyResourceResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MyResourceResponse> create(@Valid @RequestBody MyResourceRequest request) {
        MyResource saved = repository.save(new MyResource(request.name(), request.description()));
        return ResponseEntity.created(URI.create("/my-resource/" + saved.getId()))
            .body(MyResourceResponse.from(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MyResourceResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody MyResourceRequest request) {
        return repository.findById(id)
            .map(existing -> {
                existing.setName(request.name());
                existing.setDescription(request.description());
                return ResponseEntity.ok(MyResourceResponse.from(repository.save(existing)));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
```

**Règles** :
- Toujours retourner `ResponseEntity` pour les opérations avec statuts HTTP explicites (création, 404).
- `@Valid` obligatoire sur les `@RequestBody`.
- Location header sur les `201 Created` : `URI.create("/my-resource/" + saved.getId())`.
- Pour `update`, charger l'entité existante depuis le repository avant de la modifier — ne jamais forcer un id sur un objet non géré par JPA.

---

## Gestion des erreurs

Un `@RestControllerAdvice` global gère les cas transversaux :
- `MethodArgumentNotValidException` → 400 avec détail des champs
- `ResponseStatusException` → statut HTTP correspondant
- Exception non gérée → 500 sans stacktrace en réponse

Ne pas gérer `MethodArgumentNotValidException` dans les controllers individuels.

---

## Entité JPA

```java
@Entity
@Table(name = "my_resources")
public class MyResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    public MyResource() {}

    public MyResource(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // getters + setters
}
```

**Règles** :
- Constructeur sans argument obligatoire pour JPA.
- Constructeur avec paramètres pour la création depuis un DTO (évite les setters en cascade dans le controller).
- `@NotBlank` / `@NotNull` sur les champs obligatoires (Bean Validation).
- Pas de `@Data` Lombok : getters/setters explicites pour garder le contrôle sur l'API publique de l'entité.

---

## Repository

```java
public interface MyResourceRepository extends JpaRepository<MyResource, Long> {
    // Spring Data génère les requêtes standards — ajouter uniquement si besoin de queries custom
    List<MyResource> findByNameContainingIgnoreCase(String name);
}
```

---

## Migration Flyway

```sql
-- src/main/resources/db/migration/V2__add_my_resource.sql
CREATE TABLE my_resources (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT
);
```

**Règles** :
- Numérotation séquentielle : `V1__`, `V2__`, `V3__`...
- Ne jamais modifier un script existant après qu'il a été appliqué.
- Les données de seed vont dans le même fichier que la table, ou dans un script `R__` (repeatable).

---

## Accès à l'utilisateur connecté

```java
@GetMapping("/me")
@PreAuthorize("hasRole('USER')")
public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
    return Map.of(
        "sub",   jwt.getSubject(),
        "email", jwt.getClaimAsString("email")
    );
}
```

Les rôles sont dans `jwt.getClaim("realm_access")` → clé `"roles"` (List<String>).

---

## Tests

Utiliser `@SpringBootTest` + `@AutoConfigureMockMvc`. Ne pas mocker le repository — utiliser une vraie base de données (H2 ou Testcontainers PostgreSQL selon la config du projet).

```java
@SpringBootTest
@AutoConfigureMockMvc
class MyResourceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MyResourceRepository repository;

    @BeforeEach
    void setUp() { repository.deleteAll(); }

    @Test
    @WithMockUser(roles = "USER")
    void findAll_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/my-resource"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returnsCreatedWithLocation() throws Exception {
        mockMvc.perform(post("/my-resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Test", "description": "Desc"}"""))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @WithMockUser(roles = "USER")
    void create_forbiddenForUser() throws Exception {
        mockMvc.perform(post("/my-resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Test"}"""))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_rejectsMissingName() throws Exception {
        mockMvc.perform(post("/my-resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"description": "sans nom"}"""))
            .andExpect(status().isBadRequest());
    }
}
```
