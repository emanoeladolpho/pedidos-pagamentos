package io.github.emanoeladolpho.order.infrastructure.adapter.in.web.dto;

import io.github.emanoeladolpho.order.application.port.in.PageResult;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(PageResult<T> result) {
        return new PageResponse<>(result.content(), result.page(), result.size(), result.totalElements(),
                result.totalPages());
    }
}
