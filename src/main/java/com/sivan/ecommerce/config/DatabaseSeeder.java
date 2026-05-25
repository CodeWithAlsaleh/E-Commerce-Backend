package com.sivan.ecommerce.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/*
 *   Runs exactly once after the Spring Context is loaded
 *   but before the application starts taking traffic.
 * */
@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Override
    public void run(String... args) {
    }
}
