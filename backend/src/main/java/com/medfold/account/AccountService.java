package com.medfold.account;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.medfold.common.ApiException;
import com.medfold.storage.StorageService;
import com.medfold.user.User;
import com.medfold.user.UserRepository;

/** The user's own account as a whole: deleting it with everything in it. */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder, StorageService storageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.storageService = storageService;
    }

    /**
     * Permanently deletes the account. The database cascades from the user row to sessions,
     * documents and everything extracted from them, all in one transaction. Uploaded files are
     * removed once that has committed; if that fails, the orphans are logged rather than leaving
     * an account half deleted.
     */
    @Transactional
    public void delete(UUID userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please log in again"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WRONG_PASSWORD", "Password is incorrect");
        }
        userRepository.delete(user);
        userRepository.flush();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.deleteAllForUser(userId);
                } catch (RuntimeException ex) {
                    log.error("Account {} deleted but its files could not be removed", userId, ex);
                }
            }
        });
        log.info("Account {} deleted", userId);
    }
}
