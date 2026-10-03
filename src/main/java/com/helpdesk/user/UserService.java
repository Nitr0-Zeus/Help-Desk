package com.helpdesk.user;

import com.helpdesk.auth.domain.User;
import com.helpdesk.auth.repo.RefreshTokenRepository;
import com.helpdesk.auth.repo.UserRepository;
import com.helpdesk.common.error.ConflictException;
import com.helpdesk.common.error.NotFoundException;
import com.helpdesk.common.error.UnauthorizedException;
import com.helpdesk.user.dto.UserDtos.ChangePasswordRequest;
import com.helpdesk.user.dto.UserDtos.UpdateProfileRequest;
import com.helpdesk.user.dto.UserDtos.UserProfile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
    }

    public User loadRequired(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public UserProfile profile(Long userId) {
        return toProfile(loadRequired(userId));
    }

    @Transactional
    public UserProfile update(Long userId, UpdateProfileRequest request) {
        User user = loadRequired(userId);
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        if (request.email() != null && !request.email().isBlank()
                && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (users.existsByEmail(request.email())) {
                throw new ConflictException("Email already in use");
            }
            user.setEmail(request.email().trim());
        }
        user.touch(Instant.now());
        return toProfile(users.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = loadRequired(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is invalid");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.touch(Instant.now());
        users.save(user);
    }

    @Transactional
    public void deactivate(Long userId) {
        User user = loadRequired(userId);
        user.setActive(false);
        user.touch(Instant.now());
        users.save(user);
        var active = refreshTokens.findAllByUserIdAndRevokedFalse(userId);
        active.forEach(t -> t.revoke(null));
        refreshTokens.saveAll(active);
    }

    private UserProfile toProfile(User user) {
        return new UserProfile(user.getId(), user.getName(), user.getEmail(),
                user.getRole(), user.isActive());
    }
}
