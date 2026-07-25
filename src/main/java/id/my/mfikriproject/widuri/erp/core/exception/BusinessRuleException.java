package id.my.mfikriproject.widuri.erp.core.exception;

import id.my.mfikriproject.widuri.erp.core.handler.GlobalExceptionHandler;

/**
 * Thrown when a business rule or domain validation is violated at the service/entity layer.
 * Security contract: the message passed to the constructor will appear verbatim in the HTTP
 * 422 response body — use a generic, client-safe description (e.g., "Sold price is below the
 * minimum allowed price for product 1"), not internal values such as cost price or stock levels
 * from the database.
 *
 * @see GlobalExceptionHandler#handleBusinessRule
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
