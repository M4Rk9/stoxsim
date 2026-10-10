package com.stoxsim.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import com.stoxsim.auth.api.dto.*;

class PasswordByteValidationTest {
    @Test void appliesUtf8LimitToEveryPasswordInputWithoutTruncation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String password : List.of("a".repeat(72), "é".repeat(36), "😀".repeat(18))) {
                assertThat(validator.validate(new RegisterRequest("test@example.test", password, "Test", true))).isEmpty();
                assertThat(validator.validate(new LoginRequest("test@example.test", password))).isEmpty();
            }
            for (String password : List.of("a".repeat(73), "é".repeat(37), "😀".repeat(19), "क".repeat(25), "valid-text\uD800")) {
                for (Object input : List.of(
                    new RegisterRequest("test@example.test", password, "Test", true),
                    new LoginRequest("test@example.test", password),
                    new ResetPasswordRequest("token", password),
                    new PasswordUpdateRequest("valid-password", password),
                    new PasswordUpdateRequest(password, "valid-password"),
                    new ProfileUpdateRequest("test@example.test", "Test", password),
                    new DeleteAccountRequest(password))) {
                    assertThat(validator.validate(input)).as("%s", input.getClass().getSimpleName()).isNotEmpty();
                }
            }
            assertThat(validator.validate(new ProfileUpdateRequest("test@example.test", "Test"))).isEmpty();
        }
    }
}
