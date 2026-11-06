package com.misael.financialservice.services;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import com.misael.financialservice.entities.User;
import com.misael.financialservice.entities.UserType;
import com.misael.financialservice.dto.LoginRequest;
import com.misael.financialservice.dto.RegisterRequest;
import com.misael.financialservice.dto.TokenResponse;
import com.misael.financialservice.dto.UserResponse;
import com.misael.financialservice.exceptions.ApiException;
import com.misael.financialservice.mappers.UserMapper;
import com.misael.financialservice.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final UserMapper userMapper;
    private final Duration tokenExpiration;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            UserMapper userMapper,
            @Value("${app.jwt.expiration}") Duration tokenExpiration) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.userMapper = userMapper;
        if (tokenExpiration.isZero() || tokenExpiration.isNegative()) {
            throw new IllegalStateException("JWT expiration must be a positive duration.");
        }
        this.tokenExpiration = tokenExpiration;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid password",
                    "Password must not exceed 72 UTF-8 bytes.");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String document = request.document().replaceAll("[.\\-/\\s]", "");
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        if (userRepository.existsByDocument(document)) {
            throw ApiException.conflict("An account with this document already exists.");
        }
        UserType type = document.length() == 14 ? UserType.MERCHANT : UserType.COMMON;
        User user = new User(
                request.completeName().trim(),
                email,
                document,
                passwordEncoder.encode(request.password()),
                type);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(account -> account.getPasswordHash().startsWith("$2")
                        && passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(this::invalidCredentials);
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("javabank")
                .subject(user.getEmail())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(tokenExpiration))
                .claim("uid", user.getId())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", tokenExpiration.toSeconds());
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED, "Invalid credentials", "Email or password is incorrect.");
    }
}
