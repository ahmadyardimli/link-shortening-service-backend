package com.urlshortener.link_shortening_service.service;

import com.urlshortener.link_shortening_service.entity.User;
import com.urlshortener.link_shortening_service.repo.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final AppUserRepository userRepository;
    private final PasswordEncoder encoder;

    public UserService(AppUserRepository userRepository, PasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.encoder = encoder;
    }

    public User register(String email, String rawPassword) {
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new IllegalStateException("Email already in use");
        });
        User user = User.builder()
                .email(email)
                .passwordHash(encoder.encode(rawPassword))
                .build();
        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean matches(User user, String rawPassword) {
        return encoder.matches(rawPassword, user.getPasswordHash());
    }
}