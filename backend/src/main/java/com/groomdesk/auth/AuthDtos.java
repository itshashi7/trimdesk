package com.groomdesk.auth;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserProfile user) {
    }

    public record UserProfile(long id, String username, String fullName, Role role,
                              Long franchiseId, String franchiseName,
                              Long outletId, String outletCode, String outletName) {

        static UserProfile from(AppUser u) {
            return new UserProfile(u.getId(), u.getUsername(), u.getFullName(), u.getRole(),
                    u.getFranchise() == null ? null : u.getFranchise().getId(),
                    u.getFranchise() == null ? null : u.getFranchise().getName(),
                    u.getOutlet() == null ? null : u.getOutlet().getId(),
                    u.getOutlet() == null ? null : u.getOutlet().getCode(),
                    u.getOutlet() == null ? null : u.getOutlet().getName());
        }
    }
}
