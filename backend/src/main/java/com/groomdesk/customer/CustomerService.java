package com.groomdesk.customer;

import com.groomdesk.auth.AccessScope;
import com.groomdesk.auth.CurrentUser;
import com.groomdesk.auth.Role;
import com.groomdesk.billing.Bill;
import com.groomdesk.billing.BillItem;
import com.groomdesk.billing.BillItemRepository;
import com.groomdesk.billing.BillRepository;
import com.groomdesk.customer.CustomerDtos.BarberRef;
import com.groomdesk.customer.CustomerDtos.CreateCustomerRequest;
import com.groomdesk.customer.CustomerDtos.CustomerDto;
import com.groomdesk.customer.CustomerDtos.CustomerLookupDto;
import com.groomdesk.customer.CustomerDtos.OutletRef;
import com.groomdesk.customer.CustomerDtos.VisitDto;
import com.groomdesk.outlet.Outlet;
import com.groomdesk.outlet.OutletRepository;
import com.groomdesk.staff.Barber;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Customers are shared by all outlets. Billing roles (and HQ) see a customer's visits at every
 * outlet; that cross-outlet history is the point of the product (CLAUDE.md §1.2).
 * Franchise owners are excluded here; their scoped customer view comes with the profile page.
 */
@Service
public class CustomerService {

    private static final Pattern PHONE = Pattern.compile(CustomerDtos.PHONE_REGEX);
    private static final int RECENT_VISITS = 5;

    private final CustomerRepository customers;
    private final BillRepository bills;
    private final BillItemRepository billItems;
    private final OutletRepository outlets;
    private final AccessScope access;

    public CustomerService(CustomerRepository customers, BillRepository bills, BillItemRepository billItems,
                           OutletRepository outlets, AccessScope access) {
        this.customers = customers;
        this.bills = bills;
        this.billItems = billItems;
        this.outlets = outlets;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public CustomerLookupDto lookup(CurrentUser user, String phone) {
        access.requireAnyRole(user, Role.CASHIER, Role.OUTLET_MANAGER, Role.HQ_ADMIN);
        String normalized = phone == null ? "" : phone.trim();
        if (!PHONE.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone must be 10 digits starting with 6-9");
        }
        Customer customer = customers.findByPhone(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No customer with this phone"));

        List<Bill> recent = bills.findTop5ByCustomerIdOrderByCreatedAtDesc(customer.getId());
        Map<Long, List<BillItem>> itemsByBill = recent.isEmpty() ? Map.of()
                : billItems.findByBillIdIn(recent.stream().map(Bill::getId).toList()).stream()
                .collect(Collectors.groupingBy(i -> i.getBill().getId()));

        List<VisitDto> visits = recent.stream().limit(RECENT_VISITS)
                .map(b -> toVisit(b, itemsByBill.getOrDefault(b.getId(), List.of())))
                .toList();
        return new CustomerLookupDto(toDto(customer), bills.countByCustomerId(customer.getId()),
                bills.sumTotalByCustomerId(customer.getId()), visits);
    }

    /** New customer from the billing counter; their first-visit outlet is the cashier's outlet. */
    @Transactional
    public CustomerDto create(CurrentUser user, CreateCustomerRequest request) {
        long outletId = access.requireHomeOutlet(user);
        String phone = request.phone().trim();
        if (customers.findByPhone(phone).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A customer with this phone already exists");
        }
        Outlet outlet = outlets.getReferenceById(outletId);
        Customer customer = new Customer(phone, request.name().trim(), outlet);
        if (request.notes() != null && !request.notes().isBlank()) {
            customer.setNotes(request.notes().trim());
        }
        customers.saveAndFlush(customer);
        return toDto(customer);
    }

    private static CustomerDto toDto(Customer c) {
        Barber pb = c.getPreferredBarber();
        BarberRef preferred = pb == null ? null
                : new BarberRef(pb.getId(), pb.getName(), pb.getOutlet().getId(), pb.getOutlet().getCode());
        Outlet first = c.getFirstVisitOutlet();
        return new CustomerDto(c.getId(), c.getPhone(), c.getName(), c.getNotes(), preferred,
                new OutletRef(first.getId(), first.getCode(), first.getName()), c.getCreatedAt());
    }

    private static VisitDto toVisit(Bill b, List<BillItem> items) {
        Outlet o = b.getOutlet();
        return new VisitDto(b.getId(), b.getBillNo(), new OutletRef(o.getId(), o.getCode(), o.getName()),
                b.getCreatedAt(), b.getTotal(),
                items.stream().map(i -> i.getService().getName()).toList(),
                items.stream().map(i -> i.getBarber().getName()).distinct().toList());
    }
}
