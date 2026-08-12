package com.ailearning.platform.catalog.application;

import com.ailearning.platform.shared.error.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogQueryValidatorTest {

    private final CatalogQueryValidator validator = new CatalogQueryValidator();

    @Test
    void acceptsValidPriceAndPageRange() {
        CatalogQuery query = new CatalogQuery(
                "spring", "backend", null, BigDecimal.ZERO, new BigDecimal("1000000"), 0, 12
        );
        assertThatCode(() -> validator.validate(query)).doesNotThrowAnyException();
    }

    @Test
    void rejectsPageSizeAboveMaximum() {
        CatalogQuery query = new CatalogQuery(null, null, null, null, null, 0, 51);
        assertThatThrownBy(() -> validator.validate(query))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("1 đến 50");
    }

    @Test
    void rejectsInvertedPriceRange() {
        CatalogQuery query = new CatalogQuery(
                null, null, null, new BigDecimal("500000"), new BigDecimal("100000"), 0, 12
        );
        assertThatThrownBy(() -> validator.validate(query))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không được lớn hơn");
    }
}
