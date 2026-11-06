package com.misael.financialservice;

import java.util.Set;

import com.misael.financialservice.dto.RegisterRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidCpfAndCnpj() {
        assertThat(validator.validate(validRequest("52998224725"))).isEmpty();
        assertThat(validator.validate(validRequest("11222333000181"))).isEmpty();
    }

    @Test
    void rejectsInvalidDocumentsAndCredentials() {
        Set<?> invalidDocument = validator.validate(validRequest("11111111111"));
        assertThat(invalidDocument).isNotEmpty();

        RegisterRequest invalidCredentials = new RegisterRequest(
                "", "not-an-email", "short", "52998224725");
        assertThat(validator.validate(invalidCredentials)).hasSizeGreaterThanOrEqualTo(3);
    }

    private RegisterRequest validRequest(String document) {
        return new RegisterRequest("Test User", "test@example.com", "SecurePass123!", document);
    }
}
