# Exception Handling Reference Guide

This reference covers centralized exception handling for the Restaurant Backend.

---

## Architecture

Use **centralized exception handling** via `@ControllerAdvice` + `@ExceptionHandler`.

Do NOT:
- Catch and swallow exceptions silently
- Return raw exception messages to clients
- Handle exceptions in controllers individually (unless truly controller-specific)
- Use try-catch blocks everywhere

---

## Standard Error Response

Create a consistent error response structure:

```java
package com.food.foodapp.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int status;
    private String message;
    private String path;
    private LocalDateTime timestamp;
    private Map<String, String> errors; // For validation errors
}
```

---

## Custom Exceptions

### ResourceNotFoundException
```java
package com.food.foodapp.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " not found with id: " + id);
    }
}
```

### DuplicateResourceException
```java
package com.food.foodapp.exception;

public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
```

Create custom exceptions only when needed. Do not pre-create exceptions "just in case."

---

## Global Exception Handler

```java
package com.food.foodapp.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.CONFLICT.value())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Validation failed")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .errors(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("An unexpected error occurred")
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

---

## HTTP Status Code Guide

| Status | When to Use |
|---|---|
| `200 OK` | Successful GET, PUT, PATCH |
| `201 Created` | Successful POST (resource created) |
| `204 No Content` | Successful DELETE |
| `400 Bad Request` | Validation failure, malformed request |
| `404 Not Found` | Resource does not exist |
| `409 Conflict` | Duplicate resource, business rule conflict |
| `500 Internal Server Error` | Unexpected server error |

---

## Usage in Service Layer

```java
public Restaurant findById(Long id) {
    return restaurantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Restaurant", id));
}
```

Throw exceptions from the **Service layer**, not from Controllers or Repositories.

---

## Rules Summary

1. ONE global exception handler (`@RestControllerAdvice`) for the whole application
2. Consistent `ErrorResponse` structure for all error responses
3. Custom exceptions for business-specific errors
4. Throw exceptions from the Service layer
5. Use proper HTTP status codes
6. Never expose stack traces or internal error details to clients
7. Handle `MethodArgumentNotValidException` for validation errors
8. Create new custom exceptions only when a genuinely new error type appears
