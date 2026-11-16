package com.groomdesk.outlet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OutletRepository extends JpaRepository<Outlet, Long> {

    Optional<Outlet> findByCode(String code);

    List<Outlet> findByFranchiseId(Long franchiseId);

    /** Row lock on the outlet: serializes bill creation per outlet so daily bill numbers never collide. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Outlet o where o.id = :id")
    Optional<Outlet> findByIdForUpdate(@Param("id") Long id);
}
