package com.sivan.ecommerce.service.auth;

import com.sivan.ecommerce.dto.auth.AuthRequestDTO;
import com.sivan.ecommerce.dto.auth.AuthResponseDTO;
import com.sivan.ecommerce.dto.auth.RefreshRequestDTO;
import com.sivan.ecommerce.entity.customer.Customer;
import com.sivan.ecommerce.entity.token.RefreshToken;
import com.sivan.ecommerce.exception.InvalidTokenException;
import com.sivan.ecommerce.repository.customer.CustomerRepository;
import com.sivan.ecommerce.repository.token.RefreshTokenRepository;
import com.sivan.ecommerce.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CustomerRepository customerRepository;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           JwtUtil jwtUtil,
                           RefreshTokenRepository refreshTokenRepository,
                           CustomerRepository customerRepository) {

        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.refreshTokenRepository = refreshTokenRepository;
        this.customerRepository = customerRepository;
    }

    /*
     *  Login flow:
     *  1. AuthenticationManager verifies the password by calling loadUserByUsername() internally.
     *  2. If credentials are valid, we generate a short-lived Access Token (with roles in claims).
     *  3. We delete any existing refresh tokens for this customer (single-session policy).
     *  4. We create and persist a new long-lived Refresh Token in the database.
     *  5. Both tokens are returned to the client.
     */
    @Override
    @Transactional
    public AuthResponseDTO login(AuthRequestDTO authRequestDTO) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        authRequestDTO.email(),
                        authRequestDTO.password()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        String accessToken = jwtUtil.generateToken(userDetails);

        // Find the customer to associate the refresh token with
        Customer customer = customerRepository.findByEmailWithRoles(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Customer not found"));

        // Delete old refresh tokens for this customer (single-session policy)
        refreshTokenRepository.deleteAllByCustomer(customer);

        String refreshToken = createRefreshToken(customer);

        return new AuthResponseDTO(accessToken, refreshToken);
    }

    /*
     *  Refresh flow:
     *  1. Look up the refresh token in the database.
     *  2. If it doesn't exist or is expired, reject the request (the user must log in again).
     *  3. Fetch the customer with their CURRENT roles from the database.
     *     This is the checkpoint where role changes and account deactivations are caught.
     *  4. Generate a new Access Token with the updated roles.
     *  5. Rotate the refresh token (delete the old one, issue a new one) for security.
     */
    @Override
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponseDTO refresh(RefreshRequestDTO refreshRequestDTO) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshRequestDTO.refreshToken())
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new InvalidTokenException("Refresh token has expired, please log in again");
        }

        // Fetch the customer with their CURRENT roles (this is the database checkpoint)
        Customer customer = customerRepository.findByEmailWithRoles(refreshToken.getCustomer().getEmail())
                .orElseThrow(() -> new InvalidTokenException("Account no longer active"));

        // Build a fresh UserDetails with the current roles to generate the new access token
        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                customer.getEmail(),
                customer.getPassword(),
                customer.getRoles().stream()
                        .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                role.getRoleName().name()))
                        .toList()
        );

        String newAccessToken = jwtUtil.generateToken(userDetails);

        // Refresh Token Rotation: delete the old token and issue a new one
        refreshTokenRepository.delete(refreshToken);
        String newRefreshToken = createRefreshToken(customer);

        return new AuthResponseDTO(newAccessToken, newRefreshToken);
    }

    private String createRefreshToken(Customer customer) {
        /*
         *   There are generally two ways to handle Refresh Token Expiration:
         *
         *   01. Sliding Window (What we have now):
         *       Reset the timer on every refresh. Great for user experience
         *       (they never have to type their password again if they are an active user).
         *
         *   02. Absolute Expiration: The user must log in with their password
         *       every 30 days, no matter how active they are.
         *
         *   => Now we are using "Sliding Window", but if we want to use
         *      "Absolute Expiration", then we need to copy refreshToken
         *      expiryDate from the current refreshToken.
         * */
        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID().toString(),
                Instant.now().plusMillis(refreshTokenExpirationMs),
                customer
        );

        return refreshTokenRepository.save(refreshToken).getToken();
    }
}
