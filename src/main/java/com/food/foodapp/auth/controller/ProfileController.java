package com.food.foodapp.auth.controller;

import com.food.foodapp.auth.dto.ProfileResponse;
import com.food.foodapp.auth.dto.UpdateProfileRequest;
import com.food.foodapp.auth.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in user's own profile. Thin controller — {@link ProfileService} resolves the
 * caller itself via {@code UserContext}, so an anonymous request surfaces as a 401 from
 * the shared exception handler. {@code PUT} is a partial update (absent fields are left
 * unchanged) and does not accept an email change.
 */
@RestController
@RequestMapping("/api/v1/user/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile() {
        return ResponseEntity.ok(profileService.getCurrentProfile());
    }

    @PutMapping
    public ResponseEntity<ProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.updateCurrentProfile(request));
    }
}
