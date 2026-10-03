package com.helpdesk.auth.service;

import com.helpdesk.auth.api.dto.AuthDtos.AuthResponse;
import com.helpdesk.auth.api.dto.AuthDtos.LoginRequest;
import com.helpdesk.auth.api.dto.AuthDtos.RegisterRequest;
import com.helpdesk.auth.domain.InviteCode;
import com.helpdesk.auth.domain.RefreshToken;
import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.domain.User;
import com.helpdesk.auth.repo.InviteCodeRepository;
import com.helpdesk.auth.repo.RefreshTokenRepository;
import com.helpdesk.auth.repo.UserRepository;
import com.helpdesk.common.error.ConflictException;
import com.helpdesk.common.error.UnauthorizedException;
import com.helpdesk.common.time.TimeProvider;
import com.helpdesk.common.util.Hashing;
import com.helpdesk.security.JwtService;
import com.helpdesk.security.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthService {

    private final UserRepository users;
    private final InviteCodeRepository invites;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TimeProvider time;
    private final Duration refreshTtl;
    private final TransactionTemplate txTemplate;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, InviteCodeRepository invites,
                       RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtService jwtService, TimeProvider time,
                       @Value("${helpdesk.jwt.refresh-ttl}") Duration refreshTtl,
                       PlatformTransactionManager transactionManager) {
        this.users = users;
        this.invites = invites;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.time = time;
        this.refreshTtl = refreshTtl;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }
        Instant now = time.now();
        User user = new User(request.name(), request.email(),
                passwordEncoder.encode(request.password()), request.role());
        users.save(user);

        if (request.role() == Role.AGENTE) {
            InviteCode invite = request.inviteCode() == null
                    ? null
                    : invites.findById(request.inviteCode()).orElse(null);
            if (invite == null || !invite.isAvailable(now)) {
                throw new ConflictException("Invalid or expired invite code");
            }
            invite.markUsed(user, now);
            invites.save(invite);
        }
        return issueTokens(user, now);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return issueTokens(user, time.now());
    }

    /**
     * Deliberately NOT @Transactional: reuse detection must revoke the token
     * family and commit before the UnauthorizedException is thrown (which would
     * roll back any enclosing transaction).
     */
    public AuthResponse refresh(String rawToken) {
        Instant now = time.now();
        String hash = Hashing.sha256Hex(rawToken);
        var state = refreshTokens.findAuthStateByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (state.isRevoked()) {
            revokeFamily(state.getUserId());
            throw new UnauthorizedException("Refresh token reuse detected");
        }
        if (state.getExpiresAt().isBefore(now) || !state.isActive()) {
            throw new UnauthorizedException("Refresh token expired");
        }
        return rotate(hash, now);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(Hashing.sha256Hex(rawToken))
                .ifPresent(token -> {
                    token.revoke(null);
                    refreshTokens.save(token);
                });
    }

    private AuthResponse rotate(String hash, Instant now) {
        return txTemplate.execute(status -> {
            RefreshToken stored = refreshTokens.findByTokenHash(hash)
                    .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
            User user = stored.getUser();
            String newRaw = newRefreshToken();
            String newHash = Hashing.sha256Hex(newRaw);
            stored.revoke(newHash);
            refreshTokens.save(stored);
            refreshTokens.save(new RefreshToken(user, newHash, now.plus(refreshTtl)));
            return new AuthResponse(jwtService.generateAccessToken(toContext(user), now),
                    "Bearer", jwtService.accessTtlSeconds(), newRaw, now.plus(refreshTtl));
        });
    }

    private void revokeFamily(Long userId) {
        txTemplate.executeWithoutResult(status -> {
            var active = refreshTokens.findAllByUserIdAndRevokedFalse(userId);
            active.forEach(t -> t.revoke(null));
            refreshTokens.saveAll(active);
        });
    }

    private AuthResponse issueTokens(User user, Instant now) {
        String raw = newRefreshToken();
        refreshTokens.save(new RefreshToken(user, Hashing.sha256Hex(raw), now.plus(refreshTtl)));
        return new AuthResponse(jwtService.generateAccessToken(toContext(user), now),
                "Bearer", jwtService.accessTtlSeconds(), raw, now.plus(refreshTtl));
    }

    private UserContext toContext(User user) {
        return new UserContext(user.getId(), user.getEmail(), user.getRole());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
