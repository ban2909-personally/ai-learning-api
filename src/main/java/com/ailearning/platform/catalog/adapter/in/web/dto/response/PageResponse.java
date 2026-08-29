package com.ailearning.platform.catalog.adapter.in.web.dto.response;

import com.ailearning.platform.sharedkernel.pagination.PageResult;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <S, T> PageResponse<T> from(Page<S> source, Function<S, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages()
        );
    }

    public static <S, T> PageResponse<T> from(PageResult<S> source, Function<S, T> mapper) {
        return new PageResponse<>(source.content().stream().map(mapper).toList(), source.page(), source.size(),
                source.totalElements(), source.totalPages());
    }
}
