package id.my.mfikriproject.widuri.erp.core.context;

/**
 * Propagates store_id via Java Scoped Values captured from X-Store-Id header.
 * Bound once per request in StoreContextFilter; readable anywhere in the call stack
 * without passing storeId as a method parameter.
 *
 * <p><b>Threading constraint:</b> ScopedValue bindings are inherited only by the
 * carrier thread and threads spawned via {@code StructuredTaskScope}. Code that
 * runs store-scoped logic MUST either:
 * <ul>
 *   <li>Execute synchronously on the carrier thread (the default for all Spring MVC
 *       virtual-thread handlers — this is the safe path), or</li>
 *   <li>Use {@code StructuredTaskScope} when forking subtasks, which automatically
 *       propagates ScopedValue bindings to child threads.</li>
 * </ul>
 * Do NOT use raw {@code Thread.ofVirtual().start()} or {@code ExecutorService} for
 * store-scoped work — those threads do NOT inherit ScopedValue bindings and will
 * throw {@code NoSuchElementException} on {@code STORE_ID.get()}.
 *
 * <p><b>Repository convention:</b> every query on store-scoped entities MUST filter
 * by store_id. Pattern: {@code findByIdAndStoreId(Long id, Integer storeId)}.
 *
 * <p><b>Service convention:</b> call {@link #assertBound()} at the top of any
 * service method that requires store context, then use {@code STORE_ID.get()}.
 */
public final class StoreContext {

    public static final ScopedValue<Integer> STORE_ID = ScopedValue.newInstance();

    private StoreContext() {}

    /**
     * Fails fast with a meaningful error if STORE_ID is not bound on the current thread.
     * Call this at the start of any service method that requires store context rather than
     * letting a NoSuchElementException surface from deep inside a repository call.
     *
     * @throws IllegalStateException if the request did not carry a valid X-Store-Id header
     */
    public static void assertBound() {
        if (!STORE_ID.isBound()) {
            throw new IllegalStateException(
                    "StoreContext.STORE_ID is not bound on this thread. " +
                    "Ensure the request carries a valid X-Store-Id header, " +
                    "or that this code path runs within a ScopedValue carrier scope."
            );
        }
    }
}
