package com.example.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "kite_user_id", length = 32)
    private String kiteUserId;

    /** AES-256-GCM encrypted Kite access token. */
    @Column(name = "access_token", length = 2048)
    private String accessToken;

    /** AES-256-GCM encrypted Kite refresh token, if issued. */
    @Column(name = "refresh_token", length = 2048)
    private String refreshToken;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

