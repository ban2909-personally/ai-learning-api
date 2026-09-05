package com.ailearning.platform.commerce.domain.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {
    public Money {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("amount supports at most two decimal places");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be three uppercase letters");
        }
    }
}
