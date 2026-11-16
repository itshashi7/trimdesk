package com.groomdesk.billing;

import com.groomdesk.auth.CurrentUser;
import com.groomdesk.billing.BillDtos.BillDto;
import com.groomdesk.billing.BillDtos.CreateBillRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillDto create(CurrentUser user, @Valid @RequestBody CreateBillRequest request) {
        return billService.create(user, request);
    }

    @GetMapping("/{id}")
    public BillDto get(CurrentUser user, @PathVariable long id) {
        return billService.get(user, id);
    }
}
