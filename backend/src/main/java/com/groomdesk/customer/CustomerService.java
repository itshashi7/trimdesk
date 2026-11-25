package com.groomdesk.customer;

import com.groomdesk.logging.LogText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
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
import com.groomdesk.customer.CustomerDtos.CustomerProfileDto;
import com.groomdesk.customer.CustomerDtos.CustomerSearchResult;
import com.groomdesk.customer.CustomerDtos.OutletRef;
import com.groomdesk.customer.CustomerDtos.UpdateCustomerRequest;
import com.groomdesk.customer.CustomerDtos.VisitDto;
import com.groomdesk.outlet.Outlet;
import com.groomdesk.outlet.OutletRepository;
import com.groomdesk.staff.Barber;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Customers are shared by all outlets. Billing roles (and HQ) see a customer's visits at every
 * outlet; that cross-outlet history is the point of the product (docs/SPEC.md §1.2).
 * Franchise owners are excluded here; their scoped customer view comes with the profile page.
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private static final Pattern PHONE = Pattern.compile(CustomerDtos.PHONE_REGEX);
    private static final int RECENT_VISITS = 5;

    private final CustomerRepository customers;
    private final BillRepository bills;
    private final BillItemRepository billItems;
    private final OutletRepository outlets;
    private final AccessScope access;
    private final JdbcTemplate jdbc;

    public CustomerService(CustomerRepository customers, BillRepository bills, BillItemRepository billItems,
                           OutletRepository outlets, AccessScope access, JdbcTemplate jdbc) {
        this.customers = customers;
        this.bills = bills;
        this.billItems = billItems;
        this.outlets = outlets;
        this.access = access;
        this.jdbc = jdbc;
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

    /**
     * Profile with the full visit history. Managers and HQ see visits at every outlet (the cross-outlet
     * history is the product); a franchise owner sees only visits at their franchise's outlets, and a
     * customer who never visited those outlets is "not found" for them.
     */
    @Transactional(readOnly = true)
    public CustomerProfileDto profile(CurrentUser user, long customerId) {
        access.requireAnyRole(user, Role.OUTLET_MANAGER, Role.FRANCHISE_OWNER, Role.HQ_ADMIN);
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        List<Bill> all = bills.findByCustomerIdOrderByCreatedAtDesc(customerId);
        List<Bill> visible = user.role() == Role.FRANCHISE_OWNER
                ? all.stream().filter(b -> access.canAccessOutlet(user, b.getOutlet())).toList()
                : all;
        if (user.role() == Role.FRANCHISE_OWNER && visible.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }

        Map<Long, List<BillItem>> itemsByBill = visible.isEmpty() ? Map.of()
                : billItems.findByBillIdIn(visible.stream().map(Bill::getId).toList()).stream()
                .collect(Collectors.groupingBy(i -> i.getBill().getId()));
        List<VisitDto> visits = visible.stream()
                .map(b -> toVisit(b, itemsByBill.getOrDefault(b.getId(), List.of())))
                .toList();
        BigDecimal spend = visible.stream().map(Bill::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        int outletsVisited = (int) visible.stream().map(b -> b.getOutlet().getId()).distinct().count();
        Instant lastVisit = visible.isEmpty() ? null : visible.get(0).getCreatedAt();

        return new CustomerProfileDto(toDto(customer), visible.size(), spend, outletsVisited, lastVisit,
                all.size() - visible.size(), visits);
    }

    /**
     * Find customers by phone (prefix, when the query is digits) or name (contains), newest visit first.
     * Franchise owners only find customers who visited their outlets.
     */
    @Transactional(readOnly = true)
    public List<CustomerSearchResult> search(CurrentUser user, String query) {
        access.requireAnyRole(user, Role.OUTLET_MANAGER, Role.FRANCHISE_OWNER, Role.HQ_ADMIN);
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        boolean byPhone = q.chars().allMatch(Character::isDigit);
        boolean owner = user.role() == Role.FRANCHISE_OWNER;

        String visitScope = owner ? "AND b.outlet_id IN (SELECT id FROM outlet WHERE franchise_id = ?)" : "";
        String match = byPhone ? "c.phone LIKE ?" : "c.name ILIKE ? ESCAPE '\\'";
        String sql = """
                SELECT c.id, c.name, c.phone, COUNT(b.id) AS visits, MAX(b.created_at) AS last_visit
                FROM customer c LEFT JOIN bill b ON b.customer_id = c.id %s
                WHERE %s
                GROUP BY c.id, c.name, c.phone
                %s
                ORDER BY MAX(b.created_at) DESC NULLS LAST, c.name
                LIMIT 20""".formatted(visitScope, match, owner ? "HAVING COUNT(b.id) > 0" : "");

        String pattern = byPhone ? q + "%" : "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Object[] args = owner ? new Object[]{user.franchiseId(), pattern} : new Object[]{pattern};
        return jdbc.query(sql, (rs, n) -> new CustomerSearchResult(rs.getLong("id"), rs.getString("name"),
                rs.getString("phone"), rs.getLong("visits"),
                toInstant(rs.getObject("last_visit", OffsetDateTime.class))), args);
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
        Customer customer = new Customer(phone, PersonNames.normalize(request.name()), outlet);
        if (request.notes() != null && !request.notes().isBlank()) {
            customer.setNotes(request.notes().trim());
        }
        customers.saveAndFlush(customer);
        log.info("Customer {} created at outlet {} (phone {})", customer.getId(), outletId,
                LogText.maskPhones(customer.getPhone()));
        return toDto(customer);
    }

    /**
     * Corrects name, phone or notes (e.g. a typo at the counter). Allowed for billing roles and HQ.
     * A phone already used by another customer is rejected so two records never share one number.
     */
    @Transactional
    public CustomerDto update(CurrentUser user, long customerId, UpdateCustomerRequest request) {
        access.requireAnyRole(user, Role.CASHIER, Role.OUTLET_MANAGER, Role.HQ_ADMIN);
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        if (request.phone() != null) {
            String phone = request.phone().trim();
            if (!phone.equals(customer.getPhone())) {
                customers.findByPhone(phone).ifPresent(other -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Phone " + phone + " already belongs to another customer");
                });
                customer.setPhone(phone);
            }
        }
        if (request.name() != null) {
            customer.setName(PersonNames.normalize(request.name()));
        }
        if (request.notes() != null) {
            customer.setNotes(request.notes().isBlank() ? null : request.notes().trim());
        }
        customers.saveAndFlush(customer);
        List<String> fields = new ArrayList<>();
        if (request.phone() != null) fields.add("phone");
        if (request.name() != null) fields.add("name");
        if (request.notes() != null) fields.add("notes");
        log.info("Customer {} edited: {}", customer.getId(), fields);
        return toDto(customer);
    }

    private static Instant toInstant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
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
