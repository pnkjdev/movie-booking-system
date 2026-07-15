package com.moviebooking.auth.dto;

import com.moviebooking.auth.Role;
import com.moviebooking.auth.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record AuthResponse(String token, long expiresInSeconds, UserResponse user) {
    }

    public record UserResponse(Long id, String fullName, String email, Role role) {

        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
        }
    }
}
