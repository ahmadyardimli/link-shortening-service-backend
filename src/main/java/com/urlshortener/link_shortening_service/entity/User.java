package com.urlshortener.link_shortening_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "app_users", indexes = {
        @Index(name = "index_user_email_unique", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;
}
