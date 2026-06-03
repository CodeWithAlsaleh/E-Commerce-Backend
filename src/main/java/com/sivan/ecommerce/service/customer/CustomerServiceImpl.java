package com.sivan.ecommerce.service.customer;

import com.sivan.ecommerce.dto.customer.CustomerRequestDTO;
import com.sivan.ecommerce.dto.customer.CustomerResponseDTO;
import com.sivan.ecommerce.entity.cart.Cart;
import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.role.Role;
import com.sivan.ecommerce.entity.role.RoleName;
import com.sivan.ecommerce.exception.CustomerAlreadyExistsException;
import com.sivan.ecommerce.mapper.customer.CustomerMapper;
import com.sivan.ecommerce.repository.customer.CustomerRepository;
import com.sivan.ecommerce.repository.role.RoleRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public CustomerServiceImpl(CustomerRepository customerRepository,
                               RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder) {

        this.customerRepository = customerRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public CustomerResponseDTO createCustomer(CustomerRequestDTO customerRequestDTO) {
        if (customerRepository.existsByEmail(customerRequestDTO.email()))
            throw new CustomerAlreadyExistsException("Email account already exists");

        Customer customer = CustomerMapper.mapCustomerRequestToCustomer(customerRequestDTO);
        customer.setCart(new Cart());

        Optional<Role> role = roleRepository.findByRoleName(RoleName.ROLE_USER);

        if (role.isEmpty())
            throw new IllegalStateException("Default Role not found in database");

        customer.addRole(role.get());
        customer.setPassword(passwordEncoder.encode(customer.getPassword()));

        return CustomerMapper.mapCustomerToCustomerResponse(customerRepository.save(customer));
    }

    @Override
    public CustomerResponseDTO getProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return customerRepository.findByEmail(email.toLowerCase());
    }

    @Override
    // @Transactional  "Prefer to use JOIN FETCH instead for better performance"
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Optional<Customer> checkUser = customerRepository.findByEmailWithRoles(email.toLowerCase());

        if (checkUser.isEmpty())
            throw new UsernameNotFoundException("Invalid username or password");

        Customer user = checkUser.get();

        // Convert YOUR User entity → Spring Security's UserDetails object
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                mapRolesToAuthorities(user.getRoles())
        );
    }

    private Collection<? extends GrantedAuthority> mapRolesToAuthorities(Collection<Role> roles) {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getRoleName().name()))
                .collect(Collectors.toList());
    }
}
