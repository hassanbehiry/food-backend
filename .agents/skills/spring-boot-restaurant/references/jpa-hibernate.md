# JPA & Hibernate Reference Guide

This reference covers all JPA/Hibernate rules the agent must follow when working with the Restaurant Backend.

---

## Entity Design Rules

### Primary Keys
- Use `@Id` with `@GeneratedValue(strategy = GenerationType.IDENTITY)` for auto-increment
- Prefer `Long` for ID fields
- Do NOT use composite keys unless absolutely necessary

### Entity Annotations
```java
@Entity
@Table(name = "restaurants")
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // ...
}
```

### Naming
- Entity class: singular PascalCase (`Restaurant`, `MenuItem`, `OrderItem`)
- Table name: plural snake_case (`restaurants`, `menu_items`, `order_items`)
- Column name: snake_case (Hibernate default, or use `@Column(name = "...")` when needed)

---

## Relationship Rules

### @ManyToOne (most common)
```java
// MenuItem belongs to a Category
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id", nullable = false)
private Category category;
```

**Rules:**
- Always set `fetch = FetchType.LAZY` on `@ManyToOne`
- Always specify `@JoinColumn` with meaningful name
- Set `nullable = false` when the relationship is required

### @OneToMany
```java
// Category has many MenuItems
@OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
private List<MenuItem> menuItems = new ArrayList<>();
```

**Rules:**
- Always use `mappedBy` on the non-owning side
- Initialize collections: `new ArrayList<>()`
- Consider `orphanRemoval = true` when children should not exist without parent
- Be cautious with `CascadeType.ALL` — only use when the parent truly owns the lifecycle

### @OneToOne
```java
@OneToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "address_id")
private Address address;
```

**Rules:**
- Use `FetchType.LAZY`
- Question whether a @OneToOne is really needed — often a simple embedded column or @ManyToOne is better

### @ManyToMany

**CAUTION:** Avoid `@ManyToMany` in most cases for this project.
- If needed, prefer creating a join entity (e.g., `OrderItem` between `Order` and `MenuItem`)
- This gives more control and allows adding extra columns to the relationship

---

## FetchType Rules

| Relationship | Default FetchType | Recommended |
|---|---|---|
| @ManyToOne | EAGER | **LAZY** (always override) |
| @OneToOne | EAGER | **LAZY** (always override) |
| @OneToMany | LAZY | LAZY (keep default) |
| @ManyToMany | LAZY | LAZY (keep default) |

**Rule:** Never use `FetchType.EAGER` unless you have a specific, justified reason and understand the performance implications.

---

## Cascade Rules

| CascadeType | When to Use |
|---|---|
| `CascadeType.PERSIST` | When saving a parent should also save new children |
| `CascadeType.MERGE` | When updating a parent should also update children |
| `CascadeType.REMOVE` | When deleting a parent should delete children — use carefully |
| `CascadeType.ALL` | Only when the parent truly owns the complete lifecycle of children |

**Examples for Restaurant project:**
- `Order` → `OrderItem`: `CascadeType.ALL` + `orphanRemoval = true` ✅ (order items don't exist without an order)
- `Restaurant` → `Category`: Consider carefully — deleting a restaurant might cascade-delete all categories and menu items
- `Category` → `MenuItem`: `CascadeType.ALL` + `orphanRemoval = true` could be appropriate if menu items belong strictly to one category

---

## N+1 Query Problem

### What it is
When you load a list of entities and then Hibernate executes an additional query for each entity's related data.

### Example (BAD)
```java
// This triggers 1 query for restaurants + N queries for each restaurant's categories
List<Restaurant> restaurants = restaurantRepository.findAll();
restaurants.forEach(r -> r.getCategories().size()); // N+1!
```

### How to Prevent

1. **Use LAZY loading** (prevents loading unless accessed)
2. **Use `@EntityGraph` or `JOIN FETCH`** when you know you need related data:
```java
@Query("SELECT r FROM Restaurant r JOIN FETCH r.categories WHERE r.id = :id")
Optional<Restaurant> findByIdWithCategories(@Param("id") Long id);
```
3. **Use DTOs in queries** to select only needed fields
4. **Do NOT access lazy collections outside a transaction**

---

## Enum Persistence

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private OrderStatus status;
```

**Rule:** Always use `EnumType.STRING` instead of `EnumType.ORDINAL`.
- `ORDINAL` breaks if you reorder enum values
- `STRING` is readable in the database and safe to reorder

---

## Timestamps

Use `@CreationTimestamp` and `@UpdateTimestamp` from Hibernate or Spring's auditing:

```java
@CreationTimestamp
@Column(updatable = false)
private LocalDateTime createdAt;

@UpdateTimestamp
private LocalDateTime updatedAt;
```

---

## Transaction Rules

- `@Service` methods that modify data should be `@Transactional`
- Read-only operations can use `@Transactional(readOnly = true)` for optimization
- Do NOT put `@Transactional` on controllers or repositories
- Keep transactions as short as possible

---

## Common Mistakes to Prevent

| Mistake | Why it's bad | Correct approach |
|---|---|---|
| Using `EAGER` fetch by default | Loads unnecessary data, kills performance | Always use `LAZY` |
| `CascadeType.ALL` everywhere | Accidental deletes, unexpected behavior | Use specific cascade types |
| Not initializing collections | `NullPointerException` on `@OneToMany` | `new ArrayList<>()` |
| Exposing entities in API | Couples API to database, leaks internal structure | Use DTOs |
| Bidirectional relationships without helper methods | Inconsistent state | Add helper methods or manage from owning side |
| Using `@ManyToMany` with extra columns needed | Cannot add columns to join table | Create a join entity |
| Not specifying `@JoinColumn` | Hibernate generates ugly column names | Always specify join column name |
| Forgetting `nullable = false` on required fields | Allows invalid data in database | Set constraints explicitly |
