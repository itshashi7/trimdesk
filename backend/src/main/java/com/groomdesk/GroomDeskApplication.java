package com.groomdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class GroomDeskApplication {

    public static void main(String[] args) {
        // Store/compute in UTC (docs/SPEC.md §2). Also avoids Windows JVMs sending the legacy
        // "Asia/Calcutta" zone to Postgres, which rejects it on connect.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(GroomDeskApplication.class, args);
    }
}
