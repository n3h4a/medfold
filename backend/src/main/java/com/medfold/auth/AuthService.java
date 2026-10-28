package com.medfold.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medfold.auth.dto.AuthResponse;
import com.medfold.auth.dto.LoginRequest;
import com.medfold.auth.dto.RegisterRequest;
import com.medfold.common.ApiException;
import com.medfold.user.User;
import com.medfold.user.UserRepository;
import com.medfold.user.dto.UserResponse;

@Service
public class AuthService {

    /** BCrypt only uses the first 72 bytes of a password; longer input is rejected, not truncated. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Compared against when the email is unknown, so a login attempt takes about the same
     * time whether or not the account exists.
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode("medfold-dummy-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (exceedsBcryptLimit(request.password())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG",
                    "Password must be at most 72 bytes");
        }
        if (userRepository.existsByEmailNormalized(request.email())) {
            throw emailTaken();
        }

        User user = new User(request.email(), passwordEncoder.encode(request.password()), cleanName(request.fullName()));
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Another request registered the same email between the check and the insert.
            throw emailTaken();
        }
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmailNormalized(request.email());
        String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = !exceedsBcryptLimit(request.password())
                && passwordEncoder.matches(request.password(), hash);

        if (user.isEmpty() || !passwordMatches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
        }
        return issueTokens(user.get());
    }

    private AuthResponse issueTokens(User user) {
        JwtService.AccessToken token = jwtService.issueAccessToken(user);
        return AuthResponse.bearer(token.value(), token.expiresAt(), UserResponse.from(user));
    }

    private static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }

    private static String cleanName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        return fullName.trim();
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                "An account with this email already exists");
    }
}
