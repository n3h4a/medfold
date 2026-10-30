package com.medfold.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The current password, so a stolen access token alone can't erase someone's records. */
public record DeleteAccountRequest(
        @NotBlank(message = "Enter your password to confirm")
        @Size(max = 72, message = "Password is incorrect") String password) {
}
