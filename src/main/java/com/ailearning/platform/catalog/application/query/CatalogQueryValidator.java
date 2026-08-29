package com.ailearning.platform.catalog.application.query;

import com.ailearning.platform.sharedkernel.error.BusinessException;
import com.ailearning.platform.sharedkernel.error.ErrorType;

import java.math.BigDecimal;

public class CatalogQueryValidator {

    public void validate(CatalogQuery query) {
        if (query.page() < 0 || query.size() < 1 || query.size() > 50) {
            throw invalid("Trang phải >= 0 và kích thước trang phải từ 1 đến 50.");
        }
        if (isNegative(query.minPrice()) || isNegative(query.maxPrice())) {
            throw invalid("Khoảng giá không được âm.");
        }
        if (query.minPrice() != null && query.maxPrice() != null
                && query.minPrice().compareTo(query.maxPrice()) > 0) {
            throw invalid("Giá thấp nhất không được lớn hơn giá cao nhất.");
        }
    }

    private boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    private BusinessException invalid(String message) {
        return new BusinessException("invalid_catalog_query", ErrorType.BAD_REQUEST, message);
    }
}
