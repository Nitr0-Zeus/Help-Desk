package com.helpdesk.security;

import com.helpdesk.auth.domain.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public record UserContext(Long id, String email, Role role) {
    public boolean isAgent() {
        return role == Role.AGENTE;
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
