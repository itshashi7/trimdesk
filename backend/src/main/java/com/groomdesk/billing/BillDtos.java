package com.groomdesk.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class BillDtos {

    private BillDtos() {
    }

    public enum DiscountType { FLAT, PERCENT }

    /**
     * The outlet is taken from the caller's token and prices from the menu; the client only says
     * who, what, which barber, any discount and how they paid.
     */
    public record CreateBillRequest(
            @NotNull Long customerId,
            @NotEmpty @Size(max = 20) List<@Valid @NotNull Item> items,
            @Valid Discount discount,
            @NotNull PaymentMode paymentMode) {

        public record Item(@NotNull Long serviceId, @NotNull Long barberId) {
        }

        public record Discount(@NotNull DiscountType type, @NotNull @DecimalMin("0.00") BigDecimal value) {
        }
    }

    public record BillItemDto(long id, long serviceId, String serviceName, long barberId, String barberName,
                              BigDecimal price, BigDecimal commissionAmount) {
    }

    /** Everything the 80mm receipt needs. */
    public record BillDto(long id, String billNo, Instant createdAt,
                          long outletId, String outletCode, String outletName, String outletArea,
                          long customerId, String customerName, String customerPhone,
                          String cashierName,
                          List<BillItemDto> items,
                          BigDecimal subtotal, BigDecimal discount, BigDecimal taxableAmount,
                          BigDecimal taxRate, BigDecimal taxAmount, BigDecimal total,
                          PaymentMode paymentMode) {

        static BillDto from(Bill b) {
            return new BillDto(b.getId(), b.getBillNo(), b.getCreatedAt(),
                    b.getOutlet().getId(), b.getOutlet().getCode(), b.getOutlet().getName(), b.getOutlet().getArea(),
                    b.getCustomer().getId(), b.getCustomer().getName(), b.getCustomer().getPhone(),
                    b.getCashier().getFullName(),
                    b.getItems().stream().map(i -> new BillItemDto(i.getId(), i.getService().getId(),
                            i.getService().getName(), i.getBarber().getId(), i.getBarber().getName(),
                            i.getPrice(), i.getCommissionAmount())).toList(),
                    b.getSubtotal(), b.getDiscount(), b.getTaxableAmount(), b.getTaxRate(), b.getTaxAmount(),
                    b.getTotal(), b.getPaymentMode());
        }
    }
}
