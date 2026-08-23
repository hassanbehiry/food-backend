# Spring Boot Conventions Reference Guide

This reference covers Spring Boot conventions and best practices for the Restaurant Backend.

---

## Annotation Usage

### Controller Layer
```java
@RestController
@RequestMapping("/api/v1/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;

    @GetMapping
    public ResponseEntity<List<RestaurantResponse>> getAll() {
        List<RestaurantResponse> restaurants = restaurantService.findAll();
        return ResponseEntity.ok(restaurants);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantResponse> getById(@PathVariable Long id) {
        RestaurantResponse restaurant = restaurantService.findById(id);
        return ResponseEntity.ok(restaurant);
    }

    @PostMapping
    public ResponseEntity<RestaurantResponse> create(
            @Valid @RequestBody CreateRestaurantRequest request) {
        RestaurantResponse restaurant = restaurantService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurant);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RestaurantResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRestaurantRequest request) {
        RestaurantResponse restaurant = restaurantService.update(id, request);
        return ResponseEntity.ok(restaurant);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        restaurantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### Service Layer
```java
public interface RestaurantService {
    List<RestaurantResponse> findAll();
    RestaurantResponse findById(Long id);
    RestaurantResponse create(CreateRestaurantRequest request);
    RestaurantResponse update(Long id, UpdateRestaurantRequest request);
    void delete(Long id);
}
```

```java
@Service
@RequiredArgsConstructor
public class RestaurantServiceImpl implements RestaurantService {

    private final RestaurantRepository restaurantRepository;

    @Override
    public List<RestaurantResponse> findAll() {
        return restaurantRepository.findAll().stream()
                .map(RestaurantMapper::toResponse)
                .toList();
    }

    @Override
    public RestaurantResponse findById(Long id) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));
        return RestaurantMapper.toResponse(restaurant);
    }

    @Override
    @Transactional
    public RestaurantResponse create(CreateRestaurantRequest request) {
        Restaurant restaurant = RestaurantMapper.toEntity(request);
        Restaurant saved = restaurantRepository.save(restaurant);
        return RestaurantMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public RestaurantResponse update(Long id, UpdateRestaurantRequest request) {
        Restaurant restaurant = restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));

        restaurant.setName(request.getName());
        restaurant.setDescription(request.getDescription());
        restaurant.setAddress(request.getAddress());
        restaurant.setPhone(request.getPhone());

        Restaurant updated = restaurantRepository.save(restaurant);
        return RestaurantMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!restaurantRepository.existsById(id)) {
            throw new ResourceNotFoundException("Restaurant", id);
        }
        restaurantRepository.deleteById(id);
    }
}
```

### Repository Layer
```java
@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    boolean existsByName(String name);

    List<Restaurant> findByNameContainingIgnoreCase(String name);
}
```

---

## Dependency Injection Rules

### ✅ Use Constructor Injection (via Lombok)
```java
@Service
@RequiredArgsConstructor
public class RestaurantServiceImpl implements RestaurantService {
    private final RestaurantRepository restaurantRepository;
}
```

### ❌ Never Use Field Injection
```java
// BAD — Do NOT use @Autowired on fields
@Service
public class RestaurantServiceImpl implements RestaurantService {
    @Autowired
    private RestaurantRepository restaurantRepository;
}
```

**Why Constructor Injection is better:**
- Makes dependencies explicit and visible
- Enables immutability (final fields)
- Easier to understand what a class needs
- Works without Spring for manual instantiation

---

## ResponseEntity Usage

| Operation | HTTP Status | ResponseEntity |
|---|---|---|
| GET (success) | 200 OK | `ResponseEntity.ok(body)` |
| POST (created) | 201 Created | `ResponseEntity.status(HttpStatus.CREATED).body(body)` |
| PUT/PATCH (updated) | 200 OK | `ResponseEntity.ok(body)` |
| DELETE (success) | 204 No Content | `ResponseEntity.noContent().build()` |

---

## Pagination

Use Spring Data's built-in pagination:

### Controller
```java
@GetMapping
public ResponseEntity<Page<RestaurantResponse>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(defaultValue = "name") String sortBy) {

    Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
    Page<RestaurantResponse> restaurants = restaurantService.findAll(pageable);
    return ResponseEntity.ok(restaurants);
}
```

### Service
```java
@Override
public Page<RestaurantResponse> findAll(Pageable pageable) {
    return restaurantRepository.findAll(pageable)
            .map(RestaurantMapper::toResponse);
}
```

Do NOT implement custom pagination logic. Use Spring Data's `Pageable` and `Page`.

---

## Configuration

### application.properties
```properties
# Application
spring.application.name=foodapp

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Database (example for PostgreSQL)
spring.datasource.url=jdbc:postgresql://localhost:5432/foodapp
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.driver-class-name=org.postgresql.Driver
```

Do NOT use `ddl-auto=create` or `ddl-auto=create-drop` in anything resembling production.

---

## Lombok Usage

Lombok annotations used in this project:

| Annotation | Used On | Purpose |
|---|---|---|
| `@Getter` | Entities, DTOs | Generate getter methods |
| `@Setter` | Entities, Request DTOs | Generate setter methods |
| `@NoArgsConstructor` | Entities | Required by JPA |
| `@AllArgsConstructor` | When needed | Constructor with all fields |
| `@Builder` | Response DTOs | Fluent builder pattern |
| `@RequiredArgsConstructor` | Services, Controllers | Constructor injection for final fields |
| `@Data` | Use sparingly | Generates too much — prefer specific annotations |

**Rule:** Prefer `@Getter` + `@Setter` over `@Data` on entities. `@Data` generates `equals()`, `hashCode()`, and `toString()` which can cause issues with lazy-loaded JPA relationships.

---

## Bean Validation

Validation dependency (ensure it's in pom.xml):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Use `@Valid` in controllers to trigger validation:
```java
public ResponseEntity<RestaurantResponse> create(
        @Valid @RequestBody CreateRestaurantRequest request) {
```

---

## Rules Summary

1. Use `@RestController` for REST endpoints
2. Use `@Service` for business logic classes
3. Use `@Repository` for data access interfaces
4. Always use Constructor Injection (`@RequiredArgsConstructor`)
5. Never use Field Injection (`@Autowired` on fields)
6. Return `ResponseEntity` with proper HTTP status codes
7. Use Spring Data pagination, not custom implementations
8. Use `@Valid` for request validation
9. Use `@Transactional` on service methods that modify data
10. Prefer specific Lombok annotations over `@Data`
