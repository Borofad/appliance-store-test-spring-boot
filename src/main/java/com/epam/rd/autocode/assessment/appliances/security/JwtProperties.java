package com.epam.rd.autocode.assessment.appliances.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** JWT settings from application*.properties (prefix "app.jwt"). */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Signing secret, at least 32 characters. Keep it out of source control in production. */
    @NotBlank
    private String secret;

    /** How long a token stays valid, e.g. 30m. */
    @NotNull
    private Duration expiration = Duration.ofMinutes(30);

    /** Name of the cookie that carries the token in the browser. */
    @NotBlank
    private String cookieName = "APPLIANCES_JWT";

    /** Send the cookie only over HTTPS. Must be true in production, false for plain http in development. */
    private boolean cookieSecure = false;
}
