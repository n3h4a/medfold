package com.medfold.account;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medfold.account.dto.DeleteAccountRequest;
import com.medfold.auth.CurrentUserId;
import com.medfold.auth.RefreshTokenCookies;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountService accountService;
    private final RefreshTokenCookies cookies;

    public AccountController(AccountService accountService, RefreshTokenCookies cookies) {
        this.accountService = accountService;
        this.cookies = cookies;
    }

    /** Deletes the account, every document, file and extracted record, and ends the session. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@CurrentUserId UUID userId, @Valid @RequestBody DeleteAccountRequest request) {
        accountService.delete(userId, request.password());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }
}
