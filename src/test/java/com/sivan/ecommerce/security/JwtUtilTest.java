package com.sivan.ecommerce.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JwtUtil}.
 *
 * <p>No Spring context is loaded. The {@code secretKey} and {@code accessTokenExpirationMs}
 * fields are injected via reflection to keep the tests fast, isolated, and independent
 * of application.properties.</p>
 */
@DisplayName("JwtUtil")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    // A Base64-encoded 256-bit secret key used exclusively for testing
    private static final String TEST_SECRET = "dGhpc0lzQVRlc3RTZWNyZXRLZXlGb3JKd3RVbml0VGVzdHM=";
    private static final long TEST_EXPIRATION_MS = 900_000L; // 15 minutes

    private static final String TEST_EMAIL = "sivan@example.com";
    private static final String TEST_PASSWORD = "hashedPassword";
    private static final String TEST_ROLE = "ROLE_USER";

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil();

        // Inject @Value fields via reflection (no Spring context needed)
        Field secretKeyField = JwtUtil.class.getDeclaredField("secretKey");
        secretKeyField.setAccessible(true);
        secretKeyField.set(jwtUtil, TEST_SECRET);

        Field expirationField = JwtUtil.class.getDeclaredField("accessTokenExpirationMs");
        expirationField.setAccessible(true);
        expirationField.set(jwtUtil, TEST_EXPIRATION_MS);
    }

    // ======================== Helpers ========================

    /**
     * Builds a valid {@link UserDetails} with a single ROLE_USER authority.
     */
    private UserDetails validUserDetails() {
        return new User(
                TEST_EMAIL,
                TEST_PASSWORD,
                List.of(new SimpleGrantedAuthority(TEST_ROLE))
        );
    }

    /**
     * Builds a valid {@link UserDetails} with multiple roles.
     */
    private UserDetails userDetailsWithMultipleRoles() {
        return new User(
                TEST_EMAIL,
                TEST_PASSWORD,
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                )
        );
    }

    /**
     * Generates an expired token by manually building a JWT with
     * an expiration date in the past.
     */
    private String generateExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
        return Jwts.builder()
                .subject(TEST_EMAIL)
                .claim("roles", List.of(TEST_ROLE))
                .issuedAt(new Date(System.currentTimeMillis() - 200_000))
                .expiration(new Date(System.currentTimeMillis() - 100_000))
                .signWith(key)
                .compact();
    }

    /**
     * Generates a valid token signed with a completely different secret key
     * to simulate a tampered or forged token.
     */
    private String generateTokenWithDifferentSecret() {
        String differentSecret = "YURpZmZlcmVudFNlY3JldEtleUZvclRlc3RpbmdPbmx5IQ==";
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(differentSecret));
        return Jwts.builder()
                .subject(TEST_EMAIL)
                .claim("roles", List.of(TEST_ROLE))
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + TEST_EXPIRATION_MS))
                .signWith(key)
                .compact();
    }

    // ==================== generateToken() ====================
    @Nested
    @DisplayName("generateToken()")
    class GenerateToken {

        @Test
        @DisplayName("Should generate a non-null, non-blank JWT string")
        void shouldGenerateNonBlankToken() {
            // Arrange
            UserDetails userDetails = validUserDetails();

            // Act
            String token = jwtUtil.generateToken(userDetails);

            // Assert
            assertNotNull(token);
            assertFalse(token.isBlank());
        }

        @Test
        @DisplayName("Should generate a token with exactly 3 parts (Header.Payload.Signature)")
        void shouldGenerateTokenWithThreeParts() {
            // Arrange
            UserDetails userDetails = validUserDetails();

            // Act
            String token = jwtUtil.generateToken(userDetails);

            // Assert
            String[] parts = token.split("\\.");
            assertEquals(3, parts.length, "JWT must have exactly 3 dot-separated parts");
        }

        @Test
        @DisplayName("Should embed the user's email as the subject claim")
        void shouldEmbedEmailAsSubject() {
            // Arrange
            UserDetails userDetails = validUserDetails();

            // Act
            String token = jwtUtil.generateToken(userDetails);

            // Assert
            String extractedEmail = jwtUtil.extractUsername(token);
            assertEquals(TEST_EMAIL, extractedEmail);
        }

        @Test
        @DisplayName("Should embed the user's roles as a custom 'roles' claim")
        void shouldEmbedRolesInClaims() {
            // Arrange
            UserDetails userDetails = validUserDetails();

            // Act
            String token = jwtUtil.generateToken(userDetails);

            // Assert
            Claims claims = jwtUtil.extractAllClaims(token);

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            assertEquals(1, roles.size());
            assertEquals(TEST_ROLE, roles.getFirst());
        }

        @Test
        @DisplayName("Should embed multiple roles when user has more than one role")
        void shouldEmbedMultipleRoles() {
            // Arrange
            UserDetails userDetails = userDetailsWithMultipleRoles();

            // Act
            String token = jwtUtil.generateToken(userDetails);

            // Assert
            Claims claims = jwtUtil.extractAllClaims(token);

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);

            assertEquals(2, roles.size());
            assertTrue(roles.contains("ROLE_USER"));
            assertTrue(roles.contains("ROLE_ADMIN"));
        }

        @Test
        @DisplayName("Should set an expiration date in the future")
        void shouldSetFutureExpiration() {
            // Arrange
            UserDetails userDetails = validUserDetails();

            // Act
            String token = jwtUtil.generateToken(userDetails);

            // Assert
            Claims claims = jwtUtil.extractAllClaims(token);
            Date expiration = claims.getExpiration();
            assertTrue(expiration.after(new Date()), "Expiration must be in the future");
        }

        @Test
        @DisplayName("Should set the issued-at date to approximately now")
        void shouldSetIssuedAtToNow() {
            // Arrange
            UserDetails userDetails = validUserDetails();
            long before = System.currentTimeMillis();

            // Act
            String token = jwtUtil.generateToken(userDetails);

            // Assert
            long after = System.currentTimeMillis();
            Claims claims = jwtUtil.extractAllClaims(token);
            long issuedAt = claims.getIssuedAt().getTime();

            // JWT drops millisecond precision (it stores seconds since epoch).
            // We must allow a 1-second tolerance.
            assertTrue(issuedAt >= (before / 1000) * 1000 && issuedAt <= after + 1000,
                    "Issued-at should be approximately now (accounting for JWT second precision)");
        }
    }

    // ==================== extractUsername() ====================
    @Nested
    @DisplayName("extractUsername()")
    class ExtractUsername {

        @Test
        @DisplayName("Should extract the correct email from a valid token")
        void shouldExtractCorrectEmail() {
            // Arrange
            String token = jwtUtil.generateToken(validUserDetails());

            // Act
            String extractedEmail = jwtUtil.extractUsername(token);

            // Assert
            assertEquals(TEST_EMAIL, extractedEmail);
        }
    }

    // ==================== isTokenValid() ====================
    @Nested
    @DisplayName("isTokenValid()")
    class IsTokenValid {

        // ==================== SUCCESS CASES ====================
        @Nested
        @DisplayName("Success cases — valid token")
        class SuccessCases {

            @Test
            @DisplayName("Should return true for a freshly generated token")
            void shouldReturnTrueForFreshToken() {
                // Arrange
                String token = jwtUtil.generateToken(validUserDetails());

                // Act
                boolean result = jwtUtil.isTokenValid(token);

                // Assert
                assertTrue(result);
            }
        }

        // ==================== FAILURE CASES ====================
        @Nested
        @DisplayName("Failure cases — invalid token")
        class FailureCases {

            @Test
            @DisplayName("Should return false for an expired token")
            void shouldReturnFalseForExpiredToken() {
                // Arrange
                String expiredToken = generateExpiredToken();

                // Act
                boolean result = jwtUtil.isTokenValid(expiredToken);

                // Assert
                assertFalse(result);
            }

            @Test
            @DisplayName("Should return false for a token signed with a different secret key")
            void shouldReturnFalseForTamperedSignature() {
                // Arrange
                String tamperedToken = generateTokenWithDifferentSecret();

                // Act
                boolean result = jwtUtil.isTokenValid(tamperedToken);

                // Assert
                assertFalse(result);
            }

            @Test
            @DisplayName("Should return false for a completely malformed string")
            void shouldReturnFalseForMalformedToken() {
                // Arrange
                String malformed = "this.is.not.a.jwt";

                // Act
                boolean result = jwtUtil.isTokenValid(malformed);

                // Assert
                assertFalse(result);
            }

            @Test
            @DisplayName("Should return false for an empty string")
            void shouldReturnFalseForEmptyString() {
                // Act
                boolean result = jwtUtil.isTokenValid("");

                // Assert
                assertFalse(result);
            }

            @Test
            @DisplayName("Should return false for a null token")
            void shouldReturnFalseForNullToken() {
                // Act
                boolean result = jwtUtil.isTokenValid(null);

                // Assert
                assertFalse(result);
            }
        }
    }

    // ==================== extractAllClaims() ====================
    @Nested
    @DisplayName("extractAllClaims()")
    class ExtractAllClaims {

        @Test
        @DisplayName("Should throw an exception when parsing an expired token")
        void shouldThrowExceptionForExpiredToken() {
            // Arrange
            String expiredToken = generateExpiredToken();

            // Act & Assert
            assertThrows(Exception.class, () -> jwtUtil.extractAllClaims(expiredToken));
        }

        @Test
        @DisplayName("Should throw an exception when parsing a token with a wrong signature")
        void shouldThrowExceptionForWrongSignature() {
            // Arrange
            String tamperedToken = generateTokenWithDifferentSecret();

            // Act & Assert
            assertThrows(Exception.class, () -> jwtUtil.extractAllClaims(tamperedToken));
        }
    }
}
