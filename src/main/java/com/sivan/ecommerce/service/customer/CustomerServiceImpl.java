package com.sivan.ecommerce.service.customer;

import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.role.Role;
import com.sivan.ecommerce.repository.customer.CustomerRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
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
