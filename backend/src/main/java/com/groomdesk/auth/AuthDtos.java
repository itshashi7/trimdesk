package com.groomdesk.auth;

import com.groomdesk.outlet.Outlet;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserProfile user) {
    }

    /** outletTaxRate lets the billing screen preview totals; the server still computes the saved bill. */
    public record UserProfile(long id, String username, String fullName, Role role,
                              Long franchiseId, String franchiseName,
                              Long outletId, String outletCode, String outletName, BigDecimal outletTaxRate) {

        static UserProfile from(AppUser u) {
            Outlet o = u.getOutlet();
            return new UserProfile(u.getId(), u.getUsername(), u.getFullName(), u.getRole(),
                    u.getFranchise() == null ? null : u.getFranchise().getId(),
                    u.getFranchise() == null ? null : u.getFranchise().getName(),
                    o == null ? null : o.getId(),
                    o == null ? null : o.getCode(),
                    o == null ? null : o.getName(),
                    o == null ? null : o.getTaxRate());
        }
    }
}
