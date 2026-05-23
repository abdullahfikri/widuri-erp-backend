package id.my.mfikriproject.widuri.erp.core.context;

/**
 * Propagates store_id via Java Scoped Values captured from X-Store-Id header.
 * Usage: ScopedValue.where(StoreContext.STORE_ID, storeId).run(() -> { ... });
 *
 * Repository convention: every query on store-scoped entities MUST filter by store_id.
 * Pattern: findByIdAndStoreModel_Id(Long id, Integer storeId)
 * Service convention: after load, verify entity.getStoreModel().getId().equals(StoreContext.STORE_ID.get())
 */
public final class StoreContext {
    public static final ScopedValue<Integer> STORE_ID = ScopedValue.newInstance();

    private StoreContext() {}
}
