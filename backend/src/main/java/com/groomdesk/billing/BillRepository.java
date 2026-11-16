package com.groomdesk.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findByBillNo(String billNo);

    List<Bill> findTop5ByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Bill> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
