package com.medfold.user.dto;

import java.util.UUID;

import com.medfold.user.User;

public record UserResponse(UUID id, String email, String fullName) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName());
    }
}
