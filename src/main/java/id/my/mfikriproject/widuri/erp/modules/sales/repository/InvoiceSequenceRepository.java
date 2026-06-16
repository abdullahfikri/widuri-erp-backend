package id.my.mfikriproject.widuri.erp.modules.sales.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public class InvoiceSequenceRepository {
    private final JdbcClient jdbcClient;

    public InvoiceSequenceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public int getNextInvoiceSequence(Integer storeId, LocalDate date){
        return jdbcClient.sql("SELECT get_next_invoice_seq(:storeId, :date)")
                .param("storeId", storeId)
                .param("date", date)
                .query(Integer.class)
                .single();
    }
}
