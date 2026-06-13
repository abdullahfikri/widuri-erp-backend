package id.my.mfikriproject.widuri.erp.modules.inventory.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SkuRepository {
    private final JdbcClient jdbcClient;

    public SkuRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public String getNextSkuSequence() {
        return jdbcClient.sql("SELECT get_next_sku_seq()")
                .query(String.class)
                .single();
    }
}
