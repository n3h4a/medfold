package com.medfold.auth;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medfold.auth.dto.AuthResponse;
import com.medfold.auth.dto.LoginRequest;
import com.medfold.auth.dto.RegisterRequest;
import com.medfold.common.ApiError;
import com.medfold.common.ApiException;
import com.medfold.user.dto.UserResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies cookies;

    public AuthController(AuthService authService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withRefreshCookie(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(HttpStatus.OK, authService.login(request));
    }

    /** Uses the refresh-token cookie, so it works even after the access token has expired. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "${medfold.refresh-token.cookie-name:medfold_refresh}", required = false) String refreshToken) {
        return withRefreshCookie(HttpStatus.OK, authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${medfold.refresh-token.cookie-name:medfold_refresh}", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUserId UUID userId) {
        return authService.currentUser(userId);
    }

    /** A failed refresh also clears the cookie, so the browser stops sending a dead token. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getStatus());
        if ("INVALID_REFRESH_TOKEN".equals(ex.getCode())) {
            response.header(HttpHeaders.SET_COOKIE, cookies.clear().toString());
        }
        return response.body(new ApiError(ex.getStatus().value(), ex.getCode(), ex.getMessage()));
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthService.AuthSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(session.refreshToken().value()).toString())
                .body(session.body());
    }
}
