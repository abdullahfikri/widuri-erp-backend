package id.my.mfikriproject.widuri.erp.core.exception;

import id.my.mfikriproject.widuri.erp.core.handler.GlobalExceptionHandler;

/**
 * Dilempar ketika entity yang diminta tidak ditemukan di database.
 *
 * <p><strong>Kontrak keamanan:</strong> pesan yang diteruskan ke constructor akan tampil
 * verbatim di HTTP 404 response body — gunakan deskripsi generik yang aman untuk client
 * (contoh: {@code "Product not found"}), bukan nilai internal seperti raw ID atau data DB.
 *
 * @see GlobalExceptionHandler#handleEntityNotFound
 */
public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String message) {
        super(message);
    }
}
