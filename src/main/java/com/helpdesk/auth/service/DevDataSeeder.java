package com.helpdesk.auth.service;

import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.domain.User;
import com.helpdesk.auth.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class DevDataSeeder {

    @Bean
    CommandLineRunner seedDemoUsers(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            if (!users.existsByEmail("cliente@demo.com")) {
                users.save(new User("Cliente Demo", "cliente@demo.com",
                        encoder.encode("Senha@123"), Role.CLIENTE));
            }
            if (!users.existsByEmail("agente@demo.com")) {
                users.save(new User("Agente Demo", "agente@demo.com",
                        encoder.encode("Senha@123"), Role.AGENTE));
            }
        };
    }
}
