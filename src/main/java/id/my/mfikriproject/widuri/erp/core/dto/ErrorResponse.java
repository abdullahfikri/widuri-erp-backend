package id.my.mfikriproject.widuri.erp.core.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(String code, String error, List<String> details, Instant timestamp) {
    public static ErrorResponse of(String code, String error) {
        return new ErrorResponse(code, error, List.of(), Instant.now());
    }

    public static ErrorResponse of(String code, String error, List<String> details) {
        return new ErrorResponse(code, error, details, Instant.now());
    }
}
