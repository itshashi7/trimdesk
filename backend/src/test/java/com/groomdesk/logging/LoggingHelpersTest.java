package com.groomdesk.logging;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingHelpersTest {

    @Test
    void ticketUsesTheIstDateAndSixHexCharacters() {
        // 20 Nov 2026 19:00 UTC is already 21 Nov in India.
        Clock lateEvening = Clock.fixed(Instant.parse("2026-11-20T19:00:00Z"), ZoneOffset.UTC);
        String ticket = Tickets.next(lateEvening);
        assertThat(ticket).matches(Tickets.FORMAT).startsWith("TD-20261121-");
    }

    @Test
    void ticketsDoNotRepeatInPractice() {
        Clock clock = Clock.systemUTC();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(Tickets.next(clock));
        }
        assertThat(seen).hasSizeGreaterThan(990);
    }

    @Test
    void phonesAreMaskedWhereverTheyAppear() {
        assertThat(LogText.maskPhones("/api/customers/lookup?phone=9910551967"))
                .isEqualTo("/api/customers/lookup?phone=99XXXXXX67");
        assertThat(LogText.maskPhones("Phone 6235579378 already belongs to another customer"))
                .isEqualTo("Phone 62XXXXXX78 already belongs to another customer");
        // Bill numbers, amounts and ids are left alone.
        assertThat(LogText.maskPhones("TNR-20261120-0042 total 1250.00 id 12345678901"))
                .isEqualTo("TNR-20261120-0042 total 1250.00 id 12345678901");
    }

    @Test
    void untrustedTextCannotForgeExtraLogLines() {
        assertThat(LogText.clean("hq\nINFO Login: admin (HQ_ADMIN)", 100))
                .isEqualTo("hq INFO Login: admin (HQ_ADMIN)");
        assertThat(LogText.clean("x".repeat(50), 10)).isEqualTo("xxxxxxxxxx…");
        assertThat(LogText.clean(null, 10)).isEmpty();
    }

    @Test
    void rateLimiterAllowsTwentyPerMinutePerAddress() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-20T10:00:00Z"));
        ClientErrorRateLimiter limiter = new ClientErrorRateLimiter(20, 200, clock);
        for (int i = 0; i < 20; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        }
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2")).as("other addresses are unaffected").isTrue();

        clock.advanceSeconds(61);
        assertThat(limiter.tryAcquire("10.0.0.1")).as("a new minute starts a new window").isTrue();
    }

    @Test
    void rateLimiterCapsEveryoneTogetherSoRotatingAddressesStillStop() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-20T10:00:00Z"));
        ClientErrorRateLimiter limiter = new ClientErrorRateLimiter(20, 50, clock);
        for (int i = 0; i < 50; i++) {
            assertThat(limiter.tryAcquire("203.0.113." + i)).isTrue();
        }
        assertThat(limiter.tryAcquire("203.0.113.250")).as("a fresh address is over the shared cap").isFalse();

        clock.advanceSeconds(61);
        assertThat(limiter.tryAcquire("203.0.113.250")).isTrue();
    }

    @Test
    void clientAddressTrustsTheEdgeSetRealIpOnlyBehindTheProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("100.64.0.7"); // an internal Railway hop
        request.addHeader("X-Forwarded-For", "203.0.113.9");      // visitor-supplied, ignored
        request.addHeader(ClientAddress.REAL_IP, "49.37.10.20");  // overwritten by the edge

        assertThat(ClientAddress.of(request, true)).isEqualTo("49.37.10.20");
        assertThat(ClientAddress.of(request, false))
                .as("without a trusted proxy the header is ignored entirely")
                .isEqualTo("100.64.0.7");
    }

    @Test
    void clientAddressFallsBackToTheSocketForMissingOrJunkHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("100.64.0.7");
        assertThat(ClientAddress.of(request, true)).isEqualTo("100.64.0.7");

        request.addHeader(ClientAddress.REAL_IP, "x".repeat(60));
        assertThat(ClientAddress.of(request, true)).isEqualTo("100.64.0.7");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
