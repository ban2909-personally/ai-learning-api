package com.ailearning.platform.commerce.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MoneyTest {
    @Test
    void normalizesAValidPositiveAmountToDatabaseScale() {
        Money money = new Money(new BigDecimal("250000"), "VND");

        assertThat(money.amount()).isEqualByComparingTo("250000.00");
        assertThat(money.currency()).isEqualTo("VND");
    }

    @Test
    void rejectsNonPositiveUnsupportedScaleOrMalformedCurrency() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Money(BigDecimal.ZERO, "VND"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Money(new BigDecimal("1.001"), "VND"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Money(BigDecimal.ONE, "vnd"));
    }
}
