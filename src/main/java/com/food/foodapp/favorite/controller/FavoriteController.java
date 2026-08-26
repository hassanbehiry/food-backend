package com.food.foodapp.favorite.controller;

import com.food.foodapp.favorite.dto.FavoriteResponse;
import com.food.foodapp.favorite.dto.FavoriteToggleRequest;
import com.food.foodapp.favorite.dto.FavoriteToggleResponse;
import com.food.foodapp.favorite.service.FavoriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer restaurant-favorites endpoints, backing the profile favorites section.
 * Thin controller — {@link FavoriteService} resolves the caller itself via
 * {@code UserContext}. Matches the frontend's {@code userService.toggleFavorite(restaurantId)}
 * single-toggle contract rather than separate favorite/unfavorite verbs.
 */
@RestController
@RequestMapping("/api/v1/user/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** GET /api/v1/user/favorites */
    @GetMapping
    public ResponseEntity<List<FavoriteResponse>> listFavorites() {
        return ResponseEntity.ok(favoriteService.listFavorites());
    }

    /** POST /api/v1/user/favorites/toggle */
    @PostMapping("/toggle")
    public ResponseEntity<FavoriteToggleResponse> toggleFavorite(@Valid @RequestBody FavoriteToggleRequest request) {
        return ResponseEntity.ok(favoriteService.toggleFavorite(request));
    }
}
