package com.groomdesk.auth;

import com.groomdesk.auth.AuthDtos.LoginRequest;
import com.groomdesk.auth.AuthDtos.LoginResponse;
import com.groomdesk.auth.AuthDtos.UserProfile;
import com.groomdesk.logging.LogText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        AppUser user = users.findByUsername(username)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> {
                    // Username only (cleaned: it is user input); never the password.
                    log.warn("Login failed for '{}'", LogText.clean(username, 40));
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
                });
        TokenService.IssuedToken token = tokens.issue(user);
        log.info("Login: {} ({})", user.getUsername(), user.getRole());
        return new LoginResponse(token.token(), token.expiresAt(), UserProfile.from(user));
    }
}
