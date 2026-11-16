package com.groomdesk.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/** The caller, as stated by their JWT. Passed into services for role scoping. */
public record CurrentUser(long userId, String username, Role role, Long franchiseId, Long outletId) {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_FRANCHISE_ID = "franchiseId";
    public static final String CLAIM_OUTLET_ID = "outletId";

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                longClaim(jwt, CLAIM_USER_ID),
                jwt.getSubject(),
                Role.valueOf(jwt.getClaimAsString(CLAIM_ROLE)),
                optionalLongClaim(jwt, CLAIM_FRANCHISE_ID),
                optionalLongClaim(jwt, CLAIM_OUTLET_ID));
    }

    private static long longClaim(Jwt jwt, String name) {
        Long value = optionalLongClaim(jwt, name);
        if (value == null) {
            throw new IllegalArgumentException("Token is missing claim " + name);
        }
        return value;
    }

    private static Long optionalLongClaim(Jwt jwt, String name) {
        Object value = jwt.getClaim(name);
        return value instanceof Number n ? n.longValue() : null;
    }
}
