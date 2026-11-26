package com.epam.rd.autocode.assessment.appliances.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    /** 64 characters, so the same text can also sign an HS512 token in one test. */
    private static final String SECRET = "unit-test-secret-0123456789-0123456789-0123456789-0123456789-abc";

    private JwtService service;
    private UserDetails bob;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        service = new JwtService(properties(SECRET, Duration.ofMinutes(30)));
        bob = new User("bob@x.com", "HASH", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
        key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private JwtProperties properties(String secret, Duration expiration) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setExpiration(expiration);
        return properties;
    }

    private String tokenWith(String issuer, long issuedAtOffsetMillis, long expiresOffsetMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer(issuer)
                .subject("bob@x.com")
                .issuedAt(new Date(now + issuedAtOffsetMillis))
                .expiration(new Date(now + expiresOffsetMillis))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // ---------- happy path ----------

    @Test
    void generatedToken_givesBackTheUsername() {
        String token = service.generateToken(bob);

        assertEquals(Optional.of("bob@x.com"), service.extractUsername(token));
    }

    @Test
    void generatedToken_carriesRoleIssuerAndExpiry() {
        String token = service.generateToken(bob);

        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        assertEquals("EMPLOYEE", claims.get("role", String.class));
        assertEquals("appliance-store", claims.getIssuer());
        assertEquals("bob@x.com", claims.getSubject());
        long lifetime = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertTrue(Math.abs(lifetime - Duration.ofMinutes(30).toMillis()) < 2000);
    }

    @Test
    void isValid_trueForTheSameUser() {
        assertTrue(service.isValid(service.generateToken(bob), bob));
    }

    @Test
    void isValid_falseForAnotherUser() {
        UserDetails anna = new User("anna@x.com", "HASH", List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));

        assertFalse(service.isValid(service.generateToken(bob), anna));
    }

    @Test
    void getExpiration_returnsConfiguredLifetime() {
        assertEquals(Duration.ofMinutes(30), service.getExpiration());
    }

    // ---------- rejected tokens ----------

    @Test
    void tamperedSignature_isRejected() {
        String token = service.generateToken(bob);
        int signatureStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(signatureStart);
        char changed = first == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + changed + token.substring(signatureStart + 1);

        assertTrue(service.extractUsername(tampered).isEmpty());
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        JwtService other = new JwtService(properties("another-secret-0123456789-0123456789-xyz", Duration.ofMinutes(30)));

        assertTrue(service.extractUsername(other.generateToken(bob)).isEmpty());
    }

    @Test
    void expiredToken_isRejected() {
        String expired = tokenWith("appliance-store", -7_200_000, -3_600_000);

        assertTrue(service.extractUsername(expired).isEmpty());
        assertFalse(service.isValid(expired, bob));
    }

    @Test
    void tokenFromAnotherIssuer_isRejected() {
        String token = tokenWith("someone-else", 0, 3_600_000);

        assertTrue(service.extractUsername(token).isEmpty());
    }

    @Test
    void unsignedToken_isRejected() {
        String unsigned = Jwts.builder().issuer("appliance-store").subject("bob@x.com").compact();

        assertTrue(service.extractUsername(unsigned).isEmpty());
    }

    @Test
    void tokenSignedWithAnotherAlgorithm_isRejected() {
        SecretKey key512 = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        String hs512 = Jwts.builder()
                .issuer("appliance-store")
                .subject("bob@x.com")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(key512, Jwts.SIG.HS512)
                .compact();

        assertTrue(service.extractUsername(hs512).isEmpty());
    }

    @Test
    void garbageNullAndBlank_areRejected() {
        assertTrue(service.extractUsername("not-a-jwt").isEmpty());
        assertTrue(service.extractUsername("a.b.c").isEmpty());
        assertTrue(service.extractUsername("").isEmpty());
        assertTrue(service.extractUsername("   ").isEmpty());
        assertTrue(service.extractUsername(null).isEmpty());
        assertFalse(service.isValid(null, bob));
    }

    // ---------- configuration checks ----------

    @Test
    void shortSecret_isRefused() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService(properties("too-short", Duration.ofMinutes(30))));
    }

    @Test
    void missingSecret_isRefused() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService(properties(null, Duration.ofMinutes(30))));
    }

    @Test
    void nonPositiveLifetime_isRefused() {
        assertThrows(IllegalStateException.class, () -> new JwtService(properties(SECRET, Duration.ZERO)));
        assertThrows(IllegalStateException.class, () -> new JwtService(properties(SECRET, Duration.ofMinutes(-5))));
    }
}