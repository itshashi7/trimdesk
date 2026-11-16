package com.groomdesk.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BarberRepository extends JpaRepository<Barber, Long> {

    List<Barber> findByOutletIdAndActiveTrueOrderByName(Long outletId);
}
