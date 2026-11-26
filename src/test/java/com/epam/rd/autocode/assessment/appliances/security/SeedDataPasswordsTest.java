package com.epam.rd.autocode.assessment.appliances.security;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the initial data: every seeded password must be a BCrypt hash that Spring's encoder
 * accepts for the documented demo password, and no plain-text password may sneak back in.
 */
class SeedDataPasswordsTest {

    private static final Pattern ROW =
            Pattern.compile("\\('([^']*)','([^']*)','([^']*)','([^']*)'\\)");

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private static final Map<String, String> DEMO_PASSWORDS = Map.ofEntries(
            Map.entry("mercury@gmail.com", "111"),
            Map.entry("venus@gmail.com", "222"),
            Map.entry("earth@gmail.com", "333"),
            Map.entry("IamGod@gmail.com", "444"),
            Map.entry("jupiter@gmail.com", "55555"),
            Map.entry("saturn@gmail.com", "666"),
            Map.entry("uranus@gmail.com", "777"),
            Map.entry("neptune@gmail.com", "888"),
            Map.entry("phobos@gmail.com", "111"),
            Map.entry("moon@gmail.com", "222"),
            Map.entry("deimos@gmail.com", "333"),
            Map.entry("europa@gmail.com", "444"));

    /** email -> stored password, read from the SQL file on the classpath. */
    private Map<String, String> storedPasswords(String file) throws IOException {
        String sql = StreamUtils.copyToString(new ClassPathResource(file).getInputStream(), StandardCharsets.UTF_8);
        Map<String, String> result = new HashMap<>();
        Matcher matcher = ROW.matcher(sql);
        while (matcher.find()) {
            result.put(matcher.group(2), matcher.group(3));
        }
        return result;
    }

    private void assertAllHashed(Map<String, String> stored, int expectedRows) {
        assertEquals(expectedRows, stored.size());
        stored.forEach((email, hash) -> {
            assertTrue(hash.matches("\\$2[aby]\\$10\\$.{53}"), "not a BCrypt hash for " + email);
            assertTrue(DEMO_PASSWORDS.containsKey(email), "unexpected user " + email);
            assertTrue(encoder.matches(DEMO_PASSWORDS.get(email), hash), "wrong hash for " + email);
        });
    }

    @Test
    void clientPasswords_areBcryptHashesOfTheDemoPasswords() throws IOException {
        assertAllHashed(storedPasswords("client.sql"), 8);
    }

    @Test
    void employeePasswords_areBcryptHashesOfTheDemoPasswords() throws IOException {
        assertAllHashed(storedPasswords("employee.sql"), 4);
    }

    @Test
    void wrongPassword_isRejected() throws IOException {
        String hash = storedPasswords("client.sql").get("mercury@gmail.com");

        assertFalse(encoder.matches("not-the-password", hash));
    }
}
