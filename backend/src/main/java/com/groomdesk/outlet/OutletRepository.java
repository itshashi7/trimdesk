package com.groomdesk.outlet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OutletRepository extends JpaRepository<Outlet, Long> {

    Optional<Outlet> findByCode(String code);

    List<Outlet> findByFranchiseId(Long franchiseId);
}
