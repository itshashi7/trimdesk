package com.groomdesk.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Receives crashes from the browser (React error screen, uncaught errors) so they show up in the
 * server logs: a customer's browser console is otherwise invisible. Open before sign-in, so it is
 * capped at 4 KB per report and 20 reports per minute per address, and every field is cleaned
 * before it is logged.
 */
@RestController
@RequestMapping("/api/client-errors")
public class ClientErrorController {

    private static final Logger log = LoggerFactory.getLogger("com.groomdesk.client");
    static final int MAX_BYTES = 4096;
    private static final int STACK_LINES = 6;

    private final ClientErrorRateLimiter limiter;

    public ClientErrorController(Clock clock) {
        this.limiter = new ClientErrorRateLimiter(20, clock);
    }

    public record ClientErrorReport(
            @NotBlank @Size(max = 40) String kind,
            @NotBlank @Size(max = 500) String message,
            @Size(max = 2000) String stack,
            @Size(max = 200) String page,
            @Size(max = 40) String relatedTicket) {
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> report(@Valid @RequestBody ClientErrorReport report,
                                                      HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        if (!limiter.tryAcquire(request.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        log.warn("Browser {} on {}: {}{} | {} | {}",
                LogText.clean(report.kind(), 40),
                LogText.safe(report.page(), 200),
                LogText.safe(report.message(), 500),
                report.relatedTicket() == null ? "" : " (after " + LogText.clean(report.relatedTicket(), 40) + ")",
                firstLines(report.stack()),
                LogText.clean(request.getHeader("User-Agent"), 120));
        // The browser shows this server ticket on its error screen.
        return ResponseEntity.accepted().body(Map.of("ticket", String.valueOf(Tickets.current())));
    }

    private static String firstLines(String stack) {
        if (stack == null || stack.isBlank()) {
            return "no stack";
        }
        return Arrays.stream(stack.split("\\R"))
                .limit(STACK_LINES)
                .map(line -> LogText.safe(line, 160))
                .collect(Collectors.joining(" ⏎ "));
    }
}
