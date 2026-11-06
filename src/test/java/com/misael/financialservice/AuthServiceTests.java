package com.misael.financialservice;

import java.time.Duration;
import java.util.Optional;

import com.misael.financialservice.dto.LoginRequest;
import com.misael.financialservice.dto.RegisterRequest;
import com.misael.financialservice.entities.User;
import com.misael.financialservice.entities.UserRole;
import com.misael.financialservice.entities.UserType;
import com.misael.financialservice.exceptions.ApiException;
import com.misael.financialservice.mappers.UserMapper;
import com.misael.financialservice.repositories.UserRepository;
import com.misael.financialservice.services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, passwordEncoder, jwtEncoder, new UserMapper(), Duration.ofMinutes(15));
    }

    @Test
    void registersAZeroBalanceCommonUserWithHashedPassword() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(userRepository.existsByDocument("52998224725")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(new RegisterRequest(
                " User ", "User@Example.com", "SecurePass123!", "529.982.247-25"));

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.wallet()).isZero();
        assertThat(response.userType()).isEqualTo(UserType.COMMON);
        var savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("$2a$encoded");
        assertThat(savedUser.getValue().getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void rejectsDuplicateEmailWithoutCreatingAnAccount() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(
                "User", "user@example.com", "SecurePass123!", "52998224725")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getStatus().value())
                .isEqualTo(409);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void issuesBearerTokenOnlyForMatchingCredentials() {
        User user = new User("User", "user@example.com", "52998224725", "$2a$encoded", UserType.COMMON);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SecurePass123!", "$2a$encoded")).thenReturn(true);
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getTokenValue()).thenReturn("signed-token");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);

        var token = authService.login(new LoginRequest("user@example.com", "SecurePass123!"));

        assertThat(token.accessToken()).isEqualTo("signed-token");
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void deniesUnknownAndLegacyPasswordHashes() {
        User legacyUser = new User("Legacy", "legacy@example.com", "52998224725", "!", UserType.COMMON);
        when(userRepository.findByEmailIgnoreCase("legacy@example.com")).thenReturn(Optional.of(legacyUser));

        assertThatThrownBy(() -> authService.login(new LoginRequest("legacy@example.com", "password")))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getStatus().value())
                .isEqualTo(401);
        verify(passwordEncoder, never()).matches(any(String.class), any(String.class));
    }
}
