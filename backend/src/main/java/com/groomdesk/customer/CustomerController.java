package com.groomdesk.customer;

import com.groomdesk.auth.CurrentUser;
import com.groomdesk.customer.CustomerDtos.CreateCustomerRequest;
import com.groomdesk.customer.CustomerDtos.CustomerDto;
import com.groomdesk.customer.CustomerDtos.CustomerLookupDto;
import com.groomdesk.customer.CustomerDtos.CustomerProfileDto;
import com.groomdesk.customer.CustomerDtos.CustomerSearchResult;
import com.groomdesk.customer.CustomerDtos.UpdateCustomerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    /** Phone prefix (digits) or name search, up to 20 results; for managers, owners and HQ. */
    @GetMapping("/search")
    public List<CustomerSearchResult> search(CurrentUser user, @RequestParam("q") String query) {
        return customerService.search(user, query);
    }

    /** Profile with the full visit history across outlets (owners: their outlets only). */
    @GetMapping("/{id}")
    public CustomerProfileDto profile(CurrentUser user, @PathVariable long id) {
        return customerService.profile(user, id);
    }

    /** 200 with customer + last 5 visits (any outlet), 404 if the phone is unknown. */
    @GetMapping("/lookup")
    public CustomerLookupDto lookup(CurrentUser user, @RequestParam String phone) {
        return customerService.lookup(user, phone);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDto create(CurrentUser user, @Valid @RequestBody CreateCustomerRequest request) {
        return customerService.create(user, request);
    }

    @PatchMapping("/{id}")
    public CustomerDto update(CurrentUser user, @PathVariable long id, @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(user, id, request);
    }
}
