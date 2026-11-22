package com.example.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    public record RegisterRequest(@NotBlank String name, @Email @NotBlank String email,
                                  @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record KiteCallbackRequest(@NotBlank String requestToken) {}

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest req) {
        UserAccount user = authService.register(req.name(), req.email(), req.password());
        return Map.of("id", user.getId(), "email", user.getEmail());
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequest req) {
        return Map.of("token", authService.login(req.email(), req.password()));
    }

    @GetMapping("/kite/login-url")
    public Map<String, String> kiteLoginUrl() {
        return Map.of("url", authService.kiteLoginUrl());
    }

    @PostMapping("/kite/callback")
    public Map<String, String> kiteCallback(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody KiteCallbackRequest req) {
        return Map.of("kiteUserId", authService.completeKiteLogin(userId, req.requestToken()));
    }
}

