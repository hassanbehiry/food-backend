# DTO Design Reference Guide

This reference covers DTO design rules for the Restaurant Backend project.

---

## Why DTOs?

DTOs (Data Transfer Objects) separate the API contract from the database model.

**Without DTOs:**
- API changes force database changes (and vice versa)
- Internal fields leak to clients (IDs, timestamps, relationships)
- Validation annotations clutter entity classes
- Circular references in JSON serialization

**With DTOs:**
- API and database evolve independently
- Control exactly what the client sees
- Clean validation on request objects
- No circular reference problems

---

## DTO Categories

### Request DTOs
Used for incoming data (POST, PUT, PATCH).

Package: `com.food.foodapp.dto.request`

```java
package com.food.foodapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRestaurantRequest {

    @NotBlank(message = "Restaurant name is required")
    @Size(max = 100, message = "Restaurant name must be at most 100 characters")
    private String name;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    @NotBlank(message = "Address is required")
    private String address;

    private String phone;
}
```

### Response DTOs
Used for outgoing data (what the client receives).

Package: `com.food.foodapp.dto.response`

```java
package com.food.foodapp.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class RestaurantResponse {

    private Long id;
    private String name;
    private String description;
    private String address;
    private String phone;
    private LocalDateTime createdAt;
}
```

---

## Naming Conventions

| Operation | DTO Name | Example |
|---|---|---|
| Create | `Create{Entity}Request` | `CreateRestaurantRequest` |
| Full Update | `Update{Entity}Request` | `UpdateRestaurantRequest` |
| Response | `{Entity}Response` | `RestaurantResponse` |

Do NOT create generic names like `RestaurantDTO` or `RestaurantData`.

---

## When to Create DTOs

**CREATE DTOs when:**
- The entity has fields that should not be exposed (internal IDs, timestamps, audit fields)
- The API request has different fields than the entity
- Validation rules are needed on incoming data
- The response shape differs from the entity shape
- Relationships would cause circular JSON serialization

**DO NOT create DTOs when:**
- The entity is trivially simple (e.g., a lookup table with only id + name)
- There is no difference between the entity and the API contract
- The DTO would be an exact copy of the entity

Always ask: *"Does this DTO serve a purpose, or is it just ceremony?"*

---

## Mapper Design

Package: `com.food.foodapp.mapper`

Use simple static methods or a Spring component. Do NOT introduce MapStruct or ModelMapper unless the project has many complex mappings.

### Simple Mapper Example

```java
package com.food.foodapp.mapper;

import com.food.foodapp.dto.request.CreateRestaurantRequest;
import com.food.foodapp.dto.response.RestaurantResponse;
import com.food.foodapp.entity.Restaurant;

public class RestaurantMapper {

    private RestaurantMapper() {
        // Utility class — prevent instantiation
    }

    public static Restaurant toEntity(CreateRestaurantRequest request) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(request.getName());
        restaurant.setDescription(request.getDescription());
        restaurant.setAddress(request.getAddress());
        restaurant.setPhone(request.getPhone());
        return restaurant;
    }

    public static RestaurantResponse toResponse(Restaurant restaurant) {
        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .description(restaurant.getDescription())
                .address(restaurant.getAddress())
                .phone(restaurant.getPhone())
                .createdAt(restaurant.getCreatedAt())
                .build();
    }
}
```

---

## Validation on Request DTOs

Place validation annotations on Request DTOs, not on entities.

### Common Annotations

| Annotation | Usage |
|---|---|
| `@NotNull` | Field must not be null |
| `@NotBlank` | String must not be null, empty, or whitespace |
| `@Size(min, max)` | String/collection length constraints |
| `@Min(value)` | Minimum numeric value |
| `@Max(value)` | Maximum numeric value |
| `@Email` | Valid email format |
| `@Positive` | Number must be positive |
| `@PositiveOrZero` | Number must be zero or positive |

### Activating Validation in Controller

```java
@PostMapping
public ResponseEntity<RestaurantResponse> create(
        @Valid @RequestBody CreateRestaurantRequest request) {
    // ...
}
```

Use `@Valid` on `@RequestBody` to trigger Bean Validation.

---

## Rules Summary

1. Request DTOs hold incoming data + validation
2. Response DTOs hold outgoing data — expose only what clients need
3. Never expose entities directly through REST APIs
4. Use `@Builder` (Lombok) on response DTOs for clean construction
5. Use `@Getter` and `@Setter` (Lombok) on request DTOs
6. Keep mappers simple — static utility methods are fine for this project
7. Do NOT create a DTO just for the sake of having one
8. Name DTOs clearly: `Create*Request`, `Update*Request`, `*Response`
