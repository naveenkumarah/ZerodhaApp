package com.example.auth;

import com.example.common.BusinessRuleException;
import com.example.common.TokenCipher;
import com.example.kite.KiteGateway;
import com.example.kite.KiteSession;
import com.example.security.JwtService;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final KiteGateway kite;
    private final TokenCipher cipher;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       KiteGateway kite, TokenCipher cipher) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.kite = kite;
        this.cipher = cipher;
    }

    @Transactional
    public UserAccount register(String name, String email, String rawPassword) {
        if (users.findByEmail(email).isPresent()) {
            throw new BusinessRuleException("Email already registered");
        }
        UserAccount user = new UserAccount();
        user.setName(name);
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setCreatedAt(Instant.now());
        return users.save(user);
    }

    public String login(String email, String rawPassword) {
        UserAccount user = users.findByEmail(email.toLowerCase())
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return jwtService.issueToken(user.getId());
    }

    public String kiteLoginUrl() {
        return kite.loginUrl();
    }

    /** Exchanges the Kite request token and stores the encrypted access token for the user. */
    @Transactional
    public String completeKiteLogin(Long userId, String requestToken) {
        KiteSession session = kite.exchangeRequestToken(requestToken);
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        user.setKiteUserId(session.kiteUserId());
        user.setAccessToken(cipher.encrypt(session.accessToken()));
        user.setRefreshToken(session.refreshToken() == null ? null : cipher.encrypt(session.refreshToken()));
        users.save(user);
        return session.kiteUserId();
    }
}

