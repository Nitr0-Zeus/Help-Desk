package com.helpdesk.user.dto;

import com.helpdesk.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserProfile(Long id, String name, String email, Role role, boolean active) {
    }

    public record UpdateProfileRequest(
            @Size(max = 120) String name,
            @Email @Size(max = 180) String email) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
