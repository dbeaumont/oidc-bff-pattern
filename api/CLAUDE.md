# Conventions Spring Boot — API (Resource Server)

Spring Boot 4.0.6 / Java 21, OAuth2 Resource Server, Flyway, PostgreSQL.
Voir le `CLAUDE.md` racine pour la stack complète et les règles de sécurité transversales.

---

## Architecture en couches

```
Controller  →  Service  →  Repository  →  PostgreSQL
```

| Couche | Responsabilité |
|---|---|
| `controller/` | Routage HTTP, validation de la requête, construction de la réponse (`ResponseEntity`) |
| `service/` | Logique applicative, règles métier, logs, levée des erreurs fonctionnelles |
| `repository/` | Accès aux données via Spring Data JPA |
| `entity/` | Modèle de persistance JPA |
| `dto/` | Objets d'entrée/sortie exposés par l'API (records Java) |

**Règles de séparation strictes :**
- Le controller ne connaît que le service — jamais le repository directement.
- Le service lève les erreurs (`ResponseStatusException`) ; le controller ne fait pas de `try/catch`.
- Aucune logique métier dans le controller ni dans l'entité JPA.
- Ne jamais exposer une entité JPA en request ou response body : toujours passer par un DTO.

---

## Règles générales

- **Injection par constructeur uniquement** — pas de `@Autowired` sur les champs.
- **Stateless** — `SessionCreationPolicy.STATELESS`, CSRF désactivé.
- **Autorisation au niveau méthode** — `@PreAuthorize` obligatoire sur chaque endpoint.
- Les rôles sont préfixés `ROLE_` par Spring Security : utiliser `hasRole('USER')` (sans préfixe) ou `hasAuthority('ROLE_USER')`.
- **Schéma géré par Flyway** — `ddl-auto: validate`. Ne jamais utiliser `create` ou `update`.

---

## Logs

- SLF4J uniquement — jamais `System.out` ni `System.err`.
- Format des messages : `[ACTION] entity=NomEntité résultat`.
- Ne jamais logger de données sensibles (secrets, mots de passe, tokens).

**correlationId**
- Un filtre lit le header `X-Correlation-Id` entrant (ou génère un UUID) et le place dans le MDC.
- Toutes les couches héritent automatiquement du `correlationId` via le MDC.

**Ce que chaque service doit logger :**
- Début d'opération (niveau `INFO`)
- Succès avec résultat (niveau `INFO`)
- Not found / avertissement métier (niveau `WARN`)
- Erreur inattendue (niveau `ERROR`)

```java
log.info("[CREATE] entity=Item début name={}", request.name());
log.info("[CREATE] entity=Item résultat id={}", saved.getId());
log.warn("[FIND_BY_ID] entity=Item NOT_FOUND id={}", id);
log.error("[DELETE] entity=Item erreur", ex);
```

**Format console :**
- Développement : lisible, inclut `%X{correlationId}`.
- Production : JSON structuré (`logstash-logback-encoder`), compatible supervision.

---

## Gestion des erreurs REST

- Un unique `@RestControllerAdvice` global gère tous les cas — jamais de `try/catch` dans les controllers.
- Le service lève `ResponseStatusException` pour les erreurs fonctionnelles (404, 409…).

**Format JSON uniforme :**
```json
{ "code": "NOT_FOUND", "message": "Item not found" }
```

**Cas couverts par le `@RestControllerAdvice` :**
- `MethodArgumentNotValidException` → 400 avec détail des champs dans `errors`
- `ResponseStatusException` → statut HTTP correspondant
- `Exception` (non gérée) → 500 sans stacktrace en réponse

---

## DTOs

Records Java uniquement (immuables, getters générés). Package `dto/`.

```java
// ItemRequest.java
public record ItemRequest(
    @NotBlank String name,
    String description
) {}

// ItemResponse.java
public record ItemResponse(Long id, String name, String description) {
    public static ItemResponse from(Item entity) {
        return new ItemResponse(entity.getId(), entity.getName(), entity.getDescription());
    }
}
```

---

## Controller

Le controller ne contient que des préoccupations HTTP : routing, validation, `ResponseEntity`.
Toute la logique est déléguée au service.

```java
@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public List<ItemResponse> findAll() {
        return itemService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ItemResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(itemService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody ItemRequest request) {
        ItemResponse created = itemService.create(request);
        return ResponseEntity.created(URI.create("/items/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ItemResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ItemRequest request) {
        return ResponseEntity.ok(itemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        itemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

**Règles :**
- `@PreAuthorize` sur chaque méthode.
- `@Valid` obligatoire sur chaque `@RequestBody`.
- `ResponseEntity` obligatoire dès qu'un statut HTTP explicite est nécessaire (201, 404…).
- Header `Location` sur les `201 Created` : `URI.create("/items/" + created.id())`.

---

## Service

Le service contient la logique applicative. Il lève les erreurs fonctionnelles et loggue les opérations.

```java
@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public ItemResponse findById(Long id) {
        log.info("[FIND_BY_ID] entity=Item début id={}", id);
        return itemRepository.findById(id)
            .map(item -> {
                log.info("[FIND_BY_ID] entity=Item résultat id={}", id);
                return ItemResponse.from(item);
            })
            .orElseThrow(() -> {
                log.warn("[FIND_BY_ID] entity=Item NOT_FOUND id={}", id);
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
            });
    }

    public ItemResponse create(ItemRequest request) {
        log.info("[CREATE] entity=Item début name={}", request.name());
        Item saved = itemRepository.save(new Item(request.name(), request.description()));
        log.info("[CREATE] entity=Item résultat id={}", saved.getId());
        return ItemResponse.from(saved);
    }
}
```

**Règles :**
- Un service par ressource métier.
- Lever `ResponseStatusException(HttpStatus.NOT_FOUND, "...")` si la ressource n'existe pas.
- Pour `update`, toujours charger l'entité existante depuis le repository avant de la modifier — ne jamais forcer un id sur un objet non géré par JPA.

---

## Repository

Interface Spring Data JPA. N'ajouter une méthode que si Spring Data ne peut pas la dériver automatiquement.

```java
public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByNameContainingIgnoreCase(String name);
}
```

---

## Entité JPA

```java
@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    public Item() {}

    public Item(String name, String description) {
        this.name = name;
        this.description = description;
    }

    // getters + setters explicites
}
```

**Règles :**
- Constructeur sans argument obligatoire pour JPA.
- Constructeur avec paramètres pour la création depuis un DTO (évite les setters en cascade dans le service).
- `@NotBlank` / `@NotNull` sur les champs obligatoires.
- Pas de `@Data` Lombok : getters/setters explicites pour maîtriser l'API publique de l'entité.
- Aucune logique métier dans l'entité.

---

## Migration Flyway

```sql
-- src/main/resources/db/migration/V2__add_items.sql
CREATE TABLE items (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT
);
```

**Règles :**
- Numérotation séquentielle : `V1__`, `V2__`, `V3__`…
- Ne jamais modifier un script déjà appliqué.
- Données de seed : même fichier que la table, ou script `R__` (repeatable).

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

Les rôles sont dans `jwt.getClaim("realm_access")` → clé `"roles"` (`List<String>`).

---

## Tests

- `@SpringBootTest` + `@AutoConfigureMockMvc` (package `org.springframework.boot.webmvc.test.autoconfigure`) pour les tests de controller.
- Ne pas mocker le repository — utiliser une vraie base H2 ou Testcontainers PostgreSQL.
- Nommage : `action_résultatAttendu` (ex : `create_returnsCreatedWithLocation`, `findById_returns404WhenMissing`).
- `@ParameterizedTest` pour couvrir les cas limites numériques.
- **Ne jamais utiliser `@WithMockUser`** sur un Resource Server JWT : Spring Security 7 rejette le `UsernamePasswordAuthenticationToken` qu'il crée. Utiliser `.with(jwt().authorities(...))` à la place.

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ItemControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ItemRepository repository;

    @BeforeEach
    void setUp() { repository.deleteAll(); }

    @Test
    void findAll_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/items")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void create_returnsCreatedWithLocation() throws Exception {
        mockMvc.perform(post("/items")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Test", "description": "Desc"}"""))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void create_rejectsMissingName() throws Exception {
        mockMvc.perform(post("/items")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"description": "sans nom"}"""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").exists());
    }
}
```

---

## Ce que Claude ne doit jamais faire

- Appeler le repository depuis un controller (passer par le service)
- Mettre de la logique métier dans un controller ou une entité JPA
- Utiliser `@Autowired` sur un champ (toujours le constructeur)
- Exposer une entité JPA directement en request ou response body
- Écrire `System.out.println` ou `System.err`
- Modifier un script Flyway déjà appliqué
- Utiliser `ddl-auto: create` ou `update`
- Importer JPA ou Spring dans un module `domain`
- Ajouter `@SpringBootTest` sur un test unitaire
- Utiliser `JobBuilderFactory` ou `StepBuilderFactory` (API Spring Batch v4 dépréciée)
- Utiliser `@WithMockUser` dans un test de Resource Server JWT (crée un `UsernamePasswordAuthenticationToken` rejeté par Spring Security 7 — utiliser `.with(jwt())` à la place)
