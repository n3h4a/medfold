package com.medfold.auth;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Returns the standard ApiError body for 401s. The frontend uses the code to decide
 * whether to refresh the token (INVALID_TOKEN) or send the user to login (UNAUTHORIZED).
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MISSING_TOKEN_BODY =
            "{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}";
    private static final String INVALID_TOKEN_BODY =
            "{\"status\":401,\"code\":\"INVALID_TOKEN\",\"message\":\"Access token is invalid or expired\"}";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        boolean invalidToken = authException instanceof OAuth2AuthenticationException;

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                invalidToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(invalidToken ? INVALID_TOKEN_BODY : MISSING_TOKEN_BODY);
    }
}
