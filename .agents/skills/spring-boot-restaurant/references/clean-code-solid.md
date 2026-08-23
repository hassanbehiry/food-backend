# Clean Code & SOLID Reference Guide

This reference covers Clean Code and SOLID principles the agent must follow and review in the Restaurant Backend.

---

## SOLID Principles

### SRP — Single Responsibility Principle

> A class should have only one reason to change.

**In the Restaurant project:**
- `RestaurantController` handles only HTTP concerns
- `RestaurantService` handles only business logic
- `RestaurantRepository` handles only data access
- `RestaurantMapper` handles only entity ↔ DTO conversion

**Violation example:**
```java
// BAD: Controller doing business logic
@PostMapping
public ResponseEntity<?> create(@RequestBody CreateRestaurantRequest request) {
    if (restaurantRepository.existsByName(request.getName())) { // Business logic in controller!
        throw new DuplicateResourceException("...");
    }
    Restaurant restaurant = new Restaurant();
    restaurant.setName(request.getName());
    restaurantRepository.save(restaurant);
    return ResponseEntity.ok(restaurant);
}
```

**Correct:** Move all business logic to the Service.

### OCP — Open/Closed Principle

> Classes should be open for extension, closed for modification.

**In the Restaurant project:**
- Use `OrderStatus` enum that can be extended with new statuses
- Design services that can handle new order types without rewriting existing logic

Do NOT over-engineer this principle. For a simple CRUD app, apply OCP naturally through clean abstractions, not through complex design patterns.

### LSP — Liskov Substitution Principle

> Subtypes must be substitutable for their base types.

**In the Restaurant project:**
- `RestaurantServiceImpl` must correctly implement `RestaurantService` interface
- All Service implementations must fulfill the contract defined by the interface

### ISP — Interface Segregation Principle

> Clients should not be forced to depend on interfaces they don't use.

**In the Restaurant project:**
- Keep service interfaces focused: `RestaurantService` has only restaurant methods
- Do NOT create a giant `ApplicationService` interface with methods for everything

### DIP — Dependency Inversion Principle

> Depend on abstractions, not on concretions.

**In the Restaurant project:**
- Controllers depend on `RestaurantService` (interface), not `RestaurantServiceImpl`
- Spring handles this automatically through DI when you inject the interface

---

## Reviewing for SOLID Violations

When a violation is found, document it as:

```
- Principle: SRP
- Severity: Major
- Problem: Controller contains duplicate-check business logic
- Why it matters: Changes to the duplicate-check rule require modifying the controller
- How to improve: Move the duplicate check to RestaurantService
```

Severity levels:
- **Critical**: Bug or will cause problems in production
- **Major**: Bad practice that leads to maintenance problems
- **Minor**: Could be improved but works fine

---

## Clean Code Rules

### Naming

| Element | Convention | Good Example | Bad Example |
|---|---|---|---|
| Class | PascalCase, noun | `RestaurantService` | `RestSvc` |
| Method | camelCase, verb | `findById()` | `restaurant()` |
| Variable | camelCase, descriptive | `restaurantName` | `rn`, `str1` |
| Constant | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` | `maxpagesize` |
| Package | lowercase | `controller` | `Controller` |
| Boolean | is/has/can prefix | `isActive` | `active`, `flag` |

### Method Design

- Methods should do **one thing**
- Keep methods short (under 20-30 lines as a guideline)
- Maximum 3-4 parameters; use an object if more are needed
- Return early to avoid deep nesting
- Avoid side effects that callers wouldn't expect

### Class Design

- Each class has one clear responsibility
- Keep classes focused — if a class does too many things, split it
- Prefer composition over inheritance
- Group related fields and methods together

### Code Smells to Flag

| Smell | Description | Severity |
|---|---|---|
| Magic numbers | Hardcoded numbers without explanation | Minor |
| Magic strings | Hardcoded strings that should be constants | Minor |
| Duplicate code | Same logic in multiple places | Major |
| Long method | Method doing too many things | Major |
| Long parameter list | More than 4 parameters | Minor |
| Dead code | Unused code, commented-out code | Minor |
| Deep nesting | More than 3 levels of if/for nesting | Major |
| God class | Class with too many responsibilities | Critical |
| Feature envy | Method uses another class's data more than its own | Major |
| Unnecessary comments | Comments that just restate the code | Minor |

### Classification System

When reviewing code, classify each finding:

**Quality:**
- ✅ **Good**: Well done, no changes needed
- 💡 **Improvement**: Could be better, not urgent
- ❌ **Problem**: Should be fixed

**Type:**
- 🐛 **Bug**: Incorrect behavior
- ⚠️ **Bad Practice**: Works but will cause problems later
- 🏗️ **Architecture Issue**: Structural problem
- 💭 **Optional Improvement**: Nice to have

---

## Java-Specific Rules

### Use Optional Correctly
```java
// GOOD: Use orElseThrow in service
public Restaurant findById(Long id) {
    return restaurantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));
}

// BAD: Don't use Optional as a method parameter
public void doSomething(Optional<String> name) { } // Don't do this

// BAD: Don't use Optional as a field
private Optional<String> description; // Don't do this
```

### Use Streams Appropriately
```java
// GOOD: Simple stream operation
List<RestaurantResponse> responses = restaurants.stream()
        .map(RestaurantMapper::toResponse)
        .toList();

// BAD: Over-complicated stream when a loop would be clearer
```

### Prefer Immutability
- Response DTOs should be immutable when possible (use `@Builder` + `@Getter`, no `@Setter`)
- Use `Collections.unmodifiableList()` when returning internal lists
- Use `final` for fields that shouldn't change after construction

### Enums
```java
// GOOD: Clear, descriptive enum
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY,
    DELIVERED,
    CANCELLED
}
```

---

## Rules Summary

1. Apply SOLID naturally — do not force patterns
2. Name everything clearly and consistently
3. Keep methods short and focused
4. Avoid code smells listed above
5. Classify issues by severity and type during reviews
6. Prefer readable Java over clever Java
7. Explain violations with WHY, not just WHAT
8. Use Optional, Streams, and Enums correctly
9. Favor composition over inheritance
10. Do NOT invent problems that don't exist
