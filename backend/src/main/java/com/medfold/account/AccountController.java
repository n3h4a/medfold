package com.medfold.account;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medfold.account.dto.AccountExport;
import com.medfold.account.dto.DeleteAccountRequest;
import com.medfold.auth.CurrentUserId;
import com.medfold.auth.LoginThrottle;
import com.medfold.auth.RefreshTokenCookies;
import com.medfold.common.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountService accountService;
    private final AccountExportService exportService;
    private final RefreshTokenCookies cookies;
    private final LoginThrottle throttle;

    public AccountController(AccountService accountService, AccountExportService exportService,
            RefreshTokenCookies cookies, LoginThrottle throttle) {
        this.accountService = accountService;
        this.exportService = exportService;
        this.cookies = cookies;
        this.throttle = throttle;
    }

    /** All of the user's data as a JSON download. Medical data, so never cached. */
    @GetMapping("/export")
    public ResponseEntity<AccountExport> export(@CurrentUserId UUID userId) {
        AccountExport export = exportService.export(userId);
        String filename = "medfold-export-" + LocalDate.ofInstant(export.exportedAt(), ZoneOffset.UTC) + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(export);
    }

    /** Deletes the account, every document, file and extracted record, and ends the session. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@CurrentUserId UUID userId, @Valid @RequestBody DeleteAccountRequest request,
            HttpServletRequest http) {
        // A stolen access token must not become a way to guess the password.
        String account = LoginThrottle.accountKey(userId.toString());
        String address = LoginThrottle.addressKey(http.getRemoteAddr());
        throttle.check(account, address);
        try {
            accountService.delete(userId, request.password());
        } catch (ApiException ex) {
            if ("WRONG_PASSWORD".equals(ex.getCode())) {
                throttle.recordFailure(account, address);
            }
            throw ex;
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }
}
