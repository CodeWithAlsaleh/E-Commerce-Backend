package com.sivan.ecommerce.config;

import com.sivan.ecommerce.entity.cart.Cart;
import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.role.Role;
import com.sivan.ecommerce.entity.role.RoleName;
import com.sivan.ecommerce.repository.customer.CustomerRepository;
import com.sivan.ecommerce.repository.role.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/*
 *   Runs exactly once after the Spring Context is loaded
 *   but before the application starts taking traffic.
 * */
@Component
public class DatabaseSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${SERVER_ADMIN_FIRST_NAME}")
    private String firstName;

    @Value("${SERVER_ADMIN_LAST_NAME}")
    private String lastName;

    @Value("${SERVER_ADMIN_EMAIL}")
    private String email;

    @Value("${SERVER_ADMIN_LOCATION}")
    private String location;

    @Value("${SERVER_ADMIN_PASSWORD}")
    private String password;

    @Autowired
    public DatabaseSeeder(CustomerRepository customerRepository, RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {

        this.customerRepository = customerRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Because of @Transactional, if anything fails here, nothing is saved.
        Role roleUser = createRoleIfNotExists(RoleName.ROLE_USER);
        Role roleAdmin = createRoleIfNotExists(RoleName.ROLE_ADMIN);

        createAdmin(roleUser, roleAdmin);
    }

    private Role createRoleIfNotExists(RoleName roleName) {
        // Look for the role. If it's not there, create it, save it, and return it.
        return roleRepository.findByRoleName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
    }

    private void createAdmin(Role roleUser, Role roleAdmin) {
        if (customerRepository.existsByEmail(email.toLowerCase()))
            return;

        Customer admin = new Customer(firstName, lastName, email.toLowerCase(), location, passwordEncoder.encode(password));

        admin.addRole(roleUser);
        admin.addRole(roleAdmin);
        admin.setCart(new Cart());

        customerRepository.save(admin);
    }
}