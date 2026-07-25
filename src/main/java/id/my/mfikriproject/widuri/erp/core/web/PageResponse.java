package id.my.mfikriproject.widuri.erp.core.web;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        Pagination pagination
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                Pagination.from(page)
        );
    }

    public record Pagination(
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean first,
            boolean last,
            boolean empty
    ) {
        public static Pagination from(Page<?> page) {
            return new Pagination(
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.hasNext(),
                    page.isFirst(),
                    page.isLast(),
                    page.isEmpty()
            );
        }
    }
}
