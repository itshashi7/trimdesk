package com.groomdesk.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.groomdesk.auth.AccessScope;
import com.groomdesk.auth.AppUser;
import com.groomdesk.auth.AppUserRepository;
import com.groomdesk.auth.CurrentUser;
import com.groomdesk.billing.BillDtos.BillDto;
import com.groomdesk.billing.BillDtos.CreateBillRequest;
import com.groomdesk.customer.Customer;
import com.groomdesk.customer.CustomerRepository;
import com.groomdesk.outlet.Outlet;
import com.groomdesk.outlet.OutletRepository;
import com.groomdesk.service.GroomingService;
import com.groomdesk.service.GroomingServiceRepository;
import com.groomdesk.staff.Barber;
import com.groomdesk.staff.BarberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BillService {

    private static final Logger log = LoggerFactory.getLogger(BillService.class);

    private static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(100);

    private final BillRepository bills;
    private final OutletRepository outlets;
    private final CustomerRepository customers;
    private final GroomingServiceRepository services;
    private final BarberRepository barbers;
    private final AppUserRepository users;
    private final BillNumberGenerator billNumbers;
    private final AccessScope access;
    private final Clock clock;

    public BillService(BillRepository bills, OutletRepository outlets, CustomerRepository customers,
                       GroomingServiceRepository services, BarberRepository barbers, AppUserRepository users,
                       BillNumberGenerator billNumbers, AccessScope access, Clock clock) {
        this.bills = bills;
        this.outlets = outlets;
        this.customers = customers;
        this.services = services;
        this.barbers = barbers;
        this.users = users;
        this.billNumbers = billNumbers;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Creates a bill at the caller's own outlet (cashier or outlet manager only).
     * Prices come from the menu, commission from each barber's %, tax from the outlet (CLAUDE.md §3).
     */
    @Transactional
    public BillDto create(CurrentUser user, CreateBillRequest request) {
        long outletId = access.requireHomeOutlet(user);
        Outlet outlet = outlets.findByIdForUpdate(outletId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Outlet not found"));
        if (!outlet.isActive()) {
            throw badRequest("Outlet " + outlet.getCode() + " is not active");
        }
        Customer customer = customers.findById(request.customerId())
                .orElseThrow(() -> badRequest("Unknown customer " + request.customerId()));

        Map<Long, GroomingService> serviceById = byId(
                services.findAllById(request.items().stream().map(CreateBillRequest.Item::serviceId).distinct().toList()),
                GroomingService::getId);
        Map<Long, Barber> barberById = byId(
                barbers.findAllById(request.items().stream().map(CreateBillRequest.Item::barberId).distinct().toList()),
                Barber::getId);

        List<BillItem> items = new ArrayList<>();
        List<BigDecimal> prices = new ArrayList<>();
        for (CreateBillRequest.Item line : request.items()) {
            GroomingService service = serviceById.get(line.serviceId());
            if (service == null || !service.isActive()) {
                throw badRequest("Unknown or inactive service " + line.serviceId());
            }
            Barber barber = barberById.get(line.barberId());
            if (barber == null || !barber.isActive() || !barber.getOutlet().getId().equals(outlet.getId())) {
                throw badRequest("Barber " + line.barberId() + " does not work at " + outlet.getCode());
            }
            BigDecimal price = service.getPrice();
            prices.add(price);
            items.add(new BillItem(service, barber, price, BillCalculator.commission(price, barber.getCommissionPercent())));
        }

        BillCalculator.Amounts amounts = BillCalculator.compute(prices, discountAmount(request, prices), outlet.getTaxRate());

        Instant now = clock.instant();
        AppUser cashier = users.getReferenceById(user.userId());
        Bill bill = new Bill(billNumbers.next(outlet.getCode(), now), outlet, customer, cashier, request.paymentMode());
        bill.setCreatedAt(now);
        bill.setSubtotal(amounts.subtotal());
        bill.setDiscount(amounts.discount());
        bill.setTaxableAmount(amounts.taxableAmount());
        bill.setTaxRate(outlet.getTaxRate());
        bill.setTaxAmount(amounts.taxAmount());
        bill.setTotal(amounts.total());
        items.forEach(bill::addItem);
        bills.saveAndFlush(bill);
        log.info("Bill {} saved: {} items, total {}, {}, customer {}", bill.getBillNo(), bill.getItems().size(),
                bill.getTotal(), bill.getPaymentMode(), customer.getId());
        return BillDto.from(bill);
    }

    /** For receipt printing; visible to anyone whose role covers the bill's outlet. */
    @Transactional(readOnly = true)
    public BillDto get(CurrentUser user, long billId) {
        Bill bill = bills.findWithDetailsById(billId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
        access.requireOutlet(user, bill.getOutlet());
        return BillDto.from(bill);
    }

    /** Flat amount as entered, or a % of the subtotal. The calculator caps it at the subtotal. */
    private static BigDecimal discountAmount(CreateBillRequest request, List<BigDecimal> prices) {
        CreateBillRequest.Discount d = request.discount();
        if (d == null) {
            return BigDecimal.ZERO;
        }
        return switch (d.type()) {
            case FLAT -> d.value();
            case PERCENT -> {
                if (d.value().compareTo(MAX_PERCENT) > 0) {
                    throw badRequest("Discount percent cannot exceed 100");
                }
                yield BillCalculator.percentOf(BillCalculator.subtotal(prices), d.value());
            }
        };
    }

    private static <T> Map<Long, T> byId(List<T> entities, Function<T, Long> id) {
        return entities.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
