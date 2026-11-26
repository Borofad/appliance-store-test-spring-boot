package com.epam.rd.autocode.assessment.appliances.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Creates and checks JSON Web Tokens (HS256).
 *
 * A token holds the login name (the email) as subject, the role, an issuer, and issue/expiry times.
 * It is signed with the application secret, so it cannot be changed or forged without that secret.
 * Anything wrong with a token (bad signature, expired, malformed, wrong algorithm or issuer) simply
 * counts as "not valid": callers never need to handle JWT exceptions.
 */
@Slf4j
@Service
public class JwtService {

    static final String ISSUER = "appliance-store";
    static final String ROLE_CLAIM = "role";
    private static final int MIN_SECRET_BYTES = 32; // 256 bits, the minimum for HS256

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        Duration lifetime = properties.getExpiration();
        if (lifetime == null || lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalStateException("app.jwt.expiration must be a positive duration");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.expiration = lifetime;
    }

    /** Creates a signed token for the user. */
    public String generateToken(UserDetails user) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .claim(ROLE_CLAIM, roleOf(user))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        log.debug("JWT issued, valid for {}", expiration);
        return token;
    }

    /** The login name stored in the token, or empty if the token is not valid. */
    public Optional<String> extractUsername(String token) {
        return parse(token).map(Claims::getSubject);
    }

    /** True if the token is valid and belongs to this user. */
    public boolean isValid(String token, UserDetails user) {
        return extractUsername(token)
                .map(username -> username.equals(user.getUsername()))
                .orElse(false);
    }

    /** How long newly issued tokens live (also used for the cookie lifetime). */
    public Duration getExpiration() {
        return expiration;
    }

    private Optional<Claims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token);
            if (!"HS256".equals(jws.getHeader().getAlgorithm())) {
                log.warn("SECURITY: JWT rejected, unexpected algorithm {}", jws.getHeader().getAlgorithm());
                return Optional.empty();
            }
            return Optional.of(jws.getPayload());
        } catch (JwtException | IllegalArgumentException ex) {
            // never log the token itself
            log.debug("JWT rejected: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** CLIENT / EMPLOYEE, taken from the user's first authority (ROLE_CLIENT -> CLIENT). */
    private String roleOf(UserDetails user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                .orElse("");
    }
}