package com.misael.financialservice.controllers;

import java.net.URI;

import com.misael.financialservice.dto.CreditRequest;
import com.misael.financialservice.dto.TransactionResponse;
import com.misael.financialservice.services.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/{id}/credits")
    @Operation(summary = "Credit an account (ADMIN only)")
    public ResponseEntity<TransactionResponse> credit(
            @PathVariable Long id,
            @Valid @RequestBody CreditRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        TransactionResponse credit = adminService.creditAccount(
                ((Number) jwt.getClaim("uid")).longValue(), id, request.amount());
        return ResponseEntity.created(URI.create("/transactions/" + credit.id())).body(credit);
    }
}
