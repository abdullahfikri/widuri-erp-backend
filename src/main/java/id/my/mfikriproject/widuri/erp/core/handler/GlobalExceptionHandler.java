package id.my.mfikriproject.widuri.erp.core.handler;

import id.my.mfikriproject.widuri.erp.core.dto.ErrorResponse;
import id.my.mfikriproject.widuri.erp.core.exception.BusinessRuleException;
import id.my.mfikriproject.widuri.erp.core.exception.DuplicateEntityException;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final String INVALID_INPUT_CODE = "INVALID_INPUT";

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ObjectOptimisticLockingFailureException ex) {
        return ErrorResponse.of("OPTIMISTIC_LOCK_CONFLICT","Product was modified by another request. Please refresh and try again");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return ErrorResponse.of("VALIDATION_FAILED", "Request validation failed", details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ErrorResponse.of(INVALID_INPUT_CODE, "Invalid path parameter type");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNotReadable(HttpMessageNotReadableException ex) {
        return ErrorResponse.of(INVALID_INPUT_CODE, "Malformed or unreadable request body");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        // Pesan error sengaja tidak menyertakan ex.getMessage() — bisa mengandung nilai raw dari DB
        return ErrorResponse.of(INVALID_INPUT_CODE, "Request contains invalid or unprocessable data");
    }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
    public ErrorResponse handleBusinessRule(BusinessRuleException ex) {
        // ex.getMessage() diteruskan by design — kontrak BusinessRuleException menjamin pesannya aman untuk client
        return ErrorResponse.of("BUSINESS_RULE_VIOLATION", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity(DataIntegrityViolationException ex) {
        return ErrorResponse.of("DATA_CONFLICT", "Data conflict or duplicate entry");
    }

    @ExceptionHandler(DuplicateEntityException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicate(DuplicateEntityException ex) {
        // ex.getMessage() diteruskan by design — kontrak DuplicateEntityException menjamin pesannya aman untuk client
        return ErrorResponse.of("DUPLICATE_ENTITY", ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleEntityNotFound(EntityNotFoundException ex) {
        // ex.getMessage() diteruskan by design — kontrak EntityNotFoundException menjamin pesannya aman untuk client
        return ErrorResponse.of("ENTITY_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(Exception ex) {
        log.error("Unhandled exception caught by GlobalExceptionHandler", ex);

        return ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred");
    }
}
