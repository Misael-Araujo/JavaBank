package com.misael.financialservice.controllers;

import java.net.URI;

import com.misael.financialservice.dto.TransactionResponse;
import com.misael.financialservice.dto.TransferRequest;
import com.misael.financialservice.services.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransferService transferService;

    public TransactionController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Operation(summary = "Transfer funds from the authenticated account")
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        TransactionResponse transaction = transferService.create(currentUserId(jwt), request);
        return ResponseEntity.created(URI.create("/transactions/" + transaction.id())).body(transaction);
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's transaction history")
    public Page<TransactionResponse> history(
            @PageableDefault(size = 20, sort = "transferDate", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return transferService.history(currentUserId(jwt), pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one transaction involving the authenticated user")
    public TransactionResponse get(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return transferService.getTransaction(currentUserId(jwt), id);
    }

    private Long currentUserId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }
}
