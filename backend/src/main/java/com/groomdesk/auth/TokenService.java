package com.groomdesk.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class TokenService {

    public record IssuedToken(String token, Instant expiresAt) {
    }

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;

    public TokenService(JwtEncoder encoder, Clock clock, @Value("${groomdesk.jwt.ttl}") Duration ttl) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = ttl;
    }

    public IssuedToken issue(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("groomdesk")
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CurrentUser.CLAIM_USER_ID, user.getId())
                .claim(CurrentUser.CLAIM_ROLE, user.getRole().name());
        if (user.getFranchise() != null) {
            claims.claim(CurrentUser.CLAIM_FRANCHISE_ID, user.getFranchise().getId());
        }
        if (user.getOutlet() != null) {
            claims.claim(CurrentUser.CLAIM_OUTLET_ID, user.getOutlet().getId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
