package com.food.foodapp.auth.service;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Component;

/**
 * Handles password hashing using BCrypt.
 * Uses standalone jBCrypt library — no Spring Security dependency.
 */
@Component
public class PasswordEncoder {

    /**
     * Hashes a raw password using BCrypt.
     */
    public String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    /**
     * Checks if a raw password matches a BCrypt hash.
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return BCrypt.checkpw(rawPassword, encodedPassword);
    }
}
