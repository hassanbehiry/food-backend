# Database Design Reference Guide

This reference covers database design rules for the Restaurant Backend.

---

## Entity-Relationship Model

The Restaurant Backend has the following core entities and relationships:

```
Restaurant (1) ──── (N) Category
Category   (1) ──── (N) MenuItem
Customer   (1) ──── (1) Cart
Cart       (1) ──── (N) CartItem
Customer   (1) ──── (N) Order
Order      (1) ──── (N) OrderItem
OrderItem  (N) ──── (1) MenuItem
Customer   (1) ──── (N) Review
Review     (N) ──── (1) Restaurant
```

Implement these relationships gradually as modules are built.

---

## Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Table name | Plural, snake_case | `restaurants`, `menu_items`, `order_items` |
| Column name | snake_case | `restaurant_name`, `created_at`, `unit_price` |
| Primary key | `id` | `id BIGINT AUTO_INCREMENT` |
| Foreign key | `{referenced_table_singular}_id` | `restaurant_id`, `category_id`, `customer_id` |
| Boolean column | `is_` prefix | `is_active`, `is_available` |
| Timestamp | `_at` suffix | `created_at`, `updated_at`, `ordered_at` |

---

## Data Types

| Java Type | SQL Type | When to Use |
|---|---|---|
| `Long` | `BIGINT` | IDs, foreign keys |
| `String` | `VARCHAR(n)` | Names, descriptions, addresses |
| `BigDecimal` | `DECIMAL(10,2)` | Prices, monetary values |
| `Integer` | `INT` | Quantities, counts |
| `Boolean` | `BOOLEAN` | Flags (is_active, is_available) |
| `LocalDateTime` | `TIMESTAMP` | Dates and times |
| `Enum` (as String) | `VARCHAR` | Status values, types |

**Important:** Always use `BigDecimal` for monetary values, never `double` or `float`.

---

## Constraints

### Primary Key
- Every table must have a primary key
- Use auto-incrementing `BIGINT` as the standard

### Not Null
- Mark required fields as `NOT NULL`
- In JPA: `@Column(nullable = false)`

### Unique
- Use for fields that must be unique (e.g., restaurant name within a system, customer email)
- In JPA: `@Column(unique = true)` or `@Table(uniqueConstraints = ...)`

### Foreign Key
- All relationships must have proper foreign keys
- In JPA: `@JoinColumn(name = "restaurant_id", nullable = false)`

### Length
- Always specify column lengths for VARCHAR fields
- In JPA: `@Column(length = 100)` or `@Column(length = 500)`

---

## Example Entity with Proper Database Design

```java
@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Boolean isAvailable = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

---

## Normalization Rules

For this project, follow at least Third Normal Form (3NF):

1. **1NF**: Each column holds a single value (no arrays or comma-separated lists)
2. **2NF**: All non-key columns depend on the entire primary key
3. **3NF**: No transitive dependencies (non-key columns don't depend on other non-key columns)

**Example of a violation:**
```java
// BAD: Storing category name in MenuItem (transitive dependency)
@Column
private String categoryName; // This duplicates data from Category table
```

**Correct:** Use a foreign key relationship to Category.

---

## Indexes

Add indexes for columns that are frequently:
- Searched/filtered on
- Used in WHERE clauses
- Used in JOIN conditions
- Used in ORDER BY clauses

```java
@Table(name = "menu_items", indexes = {
    @Index(name = "idx_menu_item_category", columnList = "category_id"),
    @Index(name = "idx_menu_item_name", columnList = "name")
})
```

Do NOT over-index. Add indexes only when there's a real query performance need.

---

## Audit Columns

Every significant entity should have:

```java
@CreationTimestamp
@Column(updatable = false)
private LocalDateTime createdAt;

@UpdateTimestamp
private LocalDateTime updatedAt;
```

---

## Common Database Design Mistakes

| Mistake | Why it's bad | Correct approach |
|---|---|---|
| Using `double` for money | Floating-point precision errors | Use `BigDecimal` |
| No constraints on required fields | Invalid data enters the database | Use `NOT NULL`, `@Column(nullable = false)` |
| Missing foreign keys | No referential integrity | Always define FK relationships |
| Storing computed values | Data inconsistency | Calculate at query time or use a view |
| Comma-separated values in a column | Violates 1NF, impossible to query efficiently | Use a separate table |
| Not specifying column lengths | Database uses maximum length, wastes space | Specify `length` on `@Column` |
| Using `ORDINAL` for enums | Adding/reordering values breaks data | Use `EnumType.STRING` |
| Missing timestamps | No audit trail | Add `createdAt` and `updatedAt` |

---

## Rules Summary

1. Follow naming conventions consistently
2. Use `BigDecimal` for all monetary values
3. Add NOT NULL constraints on required fields
4. Define foreign keys for all relationships
5. Specify column lengths for VARCHAR fields
6. Use `EnumType.STRING` for enum persistence
7. Add audit timestamps (`createdAt`, `updatedAt`)
8. Follow at least 3NF normalization
9. Add indexes only when justified by query patterns
10. Explain database decisions simply for the beginner developer
