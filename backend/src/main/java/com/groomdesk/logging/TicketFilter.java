package com.groomdesk.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;

/**
 * Runs before Spring Security, so every request (including refused and crashed ones) gets a
 * ticket: it is put in the logging context (every log line shows it), sent back in the
 * {@value Tickets#HEADER} header, and added to error bodies by ApiExceptionHandler.
 * Each API call also gets one access log line; static frontend files are not logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TicketFilter extends OncePerRequestFilter {

    private static final Logger access = LoggerFactory.getLogger("com.groomdesk.access");

    private final Clock clock;

    public TicketFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ticket = Tickets.next(clock);
        MDC.put(Tickets.MDC_KEY, ticket);
        response.setHeader(Tickets.HEADER, ticket);
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.getRequestURI().startsWith("/api/")) {
                long millis = (System.nanoTime() - started) / 1_000_000;
                Object user = request.getAttribute(RequestUserInterceptor.ATTRIBUTE);
                access.info("{} {} -> {} in {} ms · {}", request.getMethod(), describe(request),
                        response.getStatus(), millis, user == null ? "-" : user);
            }
            MDC.remove(Tickets.MDC_KEY);
        }
    }

    /** Path plus query string with phone numbers masked (lookups carry ?phone=...). */
    private static String describe(HttpServletRequest request) {
        String query = request.getQueryString();
        String target = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
        return LogText.safe(target, 200);
    }
}
