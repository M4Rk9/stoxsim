package com.stoxsim.auth.validation;

import java.nio.charset.StandardCharsets;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class Utf8PasswordValidator implements ConstraintValidator<Utf8Password, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null is handled by @NotBlank where required; profile password is optional.
        return value == null || (value.length() <= 72
            && StandardCharsets.UTF_8.newEncoder().canEncode(value)
            && value.getBytes(StandardCharsets.UTF_8).length <= 72);
    }
}
