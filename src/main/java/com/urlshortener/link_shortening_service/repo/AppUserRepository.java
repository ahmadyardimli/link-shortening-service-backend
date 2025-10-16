package com.urlshortener.link_shortening_service.repo;

import com.urlshortener.link_shortening_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}