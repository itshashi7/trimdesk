package com.groomdesk.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroomingServiceRepository extends JpaRepository<GroomingService, Long> {

    List<GroomingService> findByActiveTrueOrderByCodeAscNameAsc();
}
