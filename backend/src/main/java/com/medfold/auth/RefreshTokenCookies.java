package com.medfold.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Builds the httpOnly refresh-token cookie and the cookie that clears it. */
@Component
public class RefreshTokenCookies {

    private final RefreshTokenProperties properties;

    public RefreshTokenCookies(RefreshTokenProperties properties) {
        this.properties = properties;
    }

    public String name() {
        return properties.cookieName();
    }

    /** Cookie lifetime matches the token's lifetime in the database. */
    public ResponseCookie create(String value) {
        return base(value).maxAge(properties.ttl()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(RefreshTokenProperties.COOKIE_PATH);
    }
}
