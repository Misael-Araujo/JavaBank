package com.misael.financialservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DocumentValidator implements ConstraintValidator<ValidDocument, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String digits = value.replaceAll("[.\\-/\\s]", "");
        if (!digits.matches("\\d+")) {
            return false;
        }
        return digits.length() == 11 ? isValidCpf(digits)
                : digits.length() == 14 && isValidCnpj(digits);
    }

    private boolean isValidCpf(String cpf) {
        if (sameDigits(cpf)) {
            return false;
        }
        int first = checkDigit(cpf.substring(0, 9), new int[]{10, 9, 8, 7, 6, 5, 4, 3, 2});
        int second = checkDigit(cpf.substring(0, 9) + first,
                new int[]{11, 10, 9, 8, 7, 6, 5, 4, 3, 2});
        return cpf.endsWith("" + first + second);
    }

    private boolean isValidCnpj(String cnpj) {
        if (sameDigits(cnpj)) {
            return false;
        }
        int first = checkDigit(cnpj.substring(0, 12),
                new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int second = checkDigit(cnpj.substring(0, 12) + first,
                new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return cnpj.endsWith("" + first + second);
    }

    private int checkDigit(String value, int[] weights) {
        int sum = 0;
        for (int index = 0; index < weights.length; index++) {
            sum += Character.digit(value.charAt(index), 10) * weights[index];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private boolean sameDigits(String value) {
        return value.chars().allMatch(character -> character == value.charAt(0));
    }
}
