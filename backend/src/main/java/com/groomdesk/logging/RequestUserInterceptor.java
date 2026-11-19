package com.groomdesk.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Records who made an API call ("cashier_tnr/CASHIER") as a request attribute. TicketFilter writes
 * the access log after Spring Security has already cleared its context, so it reads the user from
 * here instead.
 */
public class RequestUserInterceptor implements HandlerInterceptor {

    static final String ATTRIBUTE = RequestUserInterceptor.class.getName() + ".user";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            String role = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring(5))
                    .findFirst()
                    .orElse("?");
            request.setAttribute(ATTRIBUTE, LogText.clean(auth.getName(), 40) + "/" + role);
        }
        return true;
    }
}
