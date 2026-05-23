package id.my.mfikriproject.widuri.erp.core.dto;

import java.util.List;

public record ErrorResponse(int status, String error, List<String> details) {
    public static ErrorResponse of(int status, String error) {
        return new ErrorResponse(status, error, List.of());
    }
}
