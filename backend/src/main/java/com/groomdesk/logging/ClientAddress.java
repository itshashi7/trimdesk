package com.groomdesk.logging;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Works out the caller's IP for rate limiting.
 *
 * <p>Behind Railway's proxy the socket address is the proxy, and the visitor's IP is in
 * {@code X-Forwarded-For}. The visitor can put anything in that header themselves; the proxy
 * <em>appends</em> the address it actually saw. So only the rightmost entry is trusted, and only
 * when {@code groomdesk.trust-proxy} says a proxy is in front (prod). Without a proxy the header
 * is ignored, since then it would be entirely client-supplied.
 */
public final class ClientAddress {

    static final String FORWARDED_FOR = "X-Forwarded-For";

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request, boolean trustProxy) {
        if (trustProxy) {
            String rightmost = rightmostEntry(request.getHeader(FORWARDED_FOR));
            if (rightmost != null) {
                return rightmost;
            }
        }
        return request.getRemoteAddr();
    }

    static String rightmostEntry(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String last = header.substring(header.lastIndexOf(',') + 1).strip();
        if (last.isEmpty() || last.length() > 45) { // 45 = longest textual IPv6 address
            return null;
        }
        return last;
    }
}
