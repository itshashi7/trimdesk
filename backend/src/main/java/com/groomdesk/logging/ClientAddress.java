package com.groomdesk.logging;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Works out the caller's IP for rate limiting.
 *
 * <p>Behind Railway's proxy the socket address is an internal proxy hop. Railway's edge
 * <em>overwrites</em> {@code X-Real-IP} with the address that actually connected, so a visitor
 * cannot spoof it. {@code X-Forwarded-For} is not used: its leftmost entry is whatever the
 * visitor sent, and the rightmost turned out to be an internal hop that changes per request.
 * The header is trusted only when {@code groomdesk.trust-proxy} says the edge is in front (prod);
 * without it the header would be entirely client-supplied.
 */
public final class ClientAddress {

    static final String REAL_IP = "X-Real-IP";
    private static final int MAX_IP_LENGTH = 45; // longest textual IPv6 address

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request, boolean trustProxy) {
        if (trustProxy) {
            String real = request.getHeader(REAL_IP);
            if (real != null) {
                real = real.strip();
                if (!real.isEmpty() && real.length() <= MAX_IP_LENGTH) {
                    return real;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
