package id.my.mfikriproject.widuri.erp.core.exception;

/**
 * Dilempar ketika entity yang akan disimpan sudah ada di database berdasarkan natural key-nya.
 *
 * <p><strong>Kontrak keamanan:</strong> pesan yang diteruskan ke constructor akan tampil
 * verbatim di HTTP 409 response body — gunakan deskripsi generik yang aman untuk client
 * (contoh: {@code "ProductGroup already exists"}), bukan nilai internal seperti raw field atau data DB.
 *
 * @see id.my.mfikriproject.widuri.erp.core.GlobalExceptionHandler#handleDuplicate
 */
public class DuplicateEntityException extends RuntimeException {
    public DuplicateEntityException(String message) {
        super(message);
    }
}
