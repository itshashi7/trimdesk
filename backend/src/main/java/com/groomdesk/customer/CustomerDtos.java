package com.groomdesk.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CustomerDtos {

    /** Exactly 10 digits, first digit 6-9 (Indian mobile). */
    public static final String PHONE_REGEX = "^[6-9]\\d{9}$";

    private CustomerDtos() {
    }

    public record CreateCustomerRequest(
            @NotBlank @Pattern(regexp = PHONE_REGEX, message = "must be 10 digits starting with 6-9") String phone,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String notes) {
    }

    /** Fix a customer's details. Omitted (null) fields stay unchanged; empty notes clears them. */
    public record UpdateCustomerRequest(
            @Pattern(regexp = PHONE_REGEX, message = "must be 10 digits starting with 6-9") String phone,
            @Pattern(regexp = ".*\\S.*", message = "must not be blank") @Size(max = 120) String name,
            @Size(max = 500) String notes) {
    }

    public record BarberRef(long id, String name, long outletId, String outletCode) {
    }

    public record OutletRef(long id, String code, String name) {
    }

    public record CustomerDto(long id, String phone, String name, String notes, BarberRef preferredBarber,
                              OutletRef firstVisitOutlet, Instant createdAt) {
    }

    /** One past bill, from any outlet. */
    public record VisitDto(long billId, String billNo, OutletRef outlet, Instant visitedAt, BigDecimal total,
                           List<String> services, List<String> barbers) {
    }

    /** Billing-screen customer card: profile, totals and the 5 most recent visits across all outlets. */
    public record CustomerLookupDto(CustomerDto customer, long totalVisits, BigDecimal totalSpend,
                                    List<VisitDto> lastVisits) {
    }
}
