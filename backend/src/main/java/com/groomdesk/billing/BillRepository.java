package com.groomdesk.billing;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {

    Optional<Bill> findByBillNo(String billNo);

    @EntityGraph(attributePaths = "outlet")
    List<Bill> findTop5ByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = "outlet")
    List<Bill> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    long countByCustomerId(Long customerId);

    @Query("select coalesce(sum(b.total), 0) from Bill b where b.customer.id = :customerId")
    BigDecimal sumTotalByCustomerId(@Param("customerId") Long customerId);

    /** Highest bill number with the given "{CODE}-{YYYYMMDD}-" prefix; zero-padded so text order = numeric order. */
    @Query("select max(b.billNo) from Bill b where b.billNo like concat(:prefix, '%')")
    String findMaxBillNoWithPrefix(@Param("prefix") String prefix);

    @EntityGraph(attributePaths = {"outlet", "customer", "cashier", "items", "items.service", "items.barber"})
    Optional<Bill> findWithDetailsById(Long id);
}
