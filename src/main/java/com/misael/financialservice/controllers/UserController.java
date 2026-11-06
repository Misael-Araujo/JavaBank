package com.misael.financialservice.controllers;

import java.util.List;

import com.misael.financialservice.dto.UserResponse;
import com.misael.financialservice.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Validated
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's account")
    public UserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return userService.getUser(currentUserId(jwt), currentUserId(jwt), isAdministrator(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account owned by the authenticated user")
    public UserResponse getUser(
            @PathVariable("id") @Positive Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.getUser(id, currentUserId(jwt), isAdministrator(jwt));
    }

    @GetMapping
    @Operation(summary = "List users (ADMIN only)")
    public Page<UserResponse> listAll(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return userService.listAll(pageable);
    }

    private Long currentUserId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean isAdministrator(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }
}
