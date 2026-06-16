package id.my.mfikriproject.widuri.erp.modules.sales.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for {@link InvoiceSequenceRepository} running against real PostgreSQL.
 * <p>
 * The container is self-provisioned by Testcontainers + {@code @ServiceConnection}; no manual
 * {@code docker compose up -d} is needed. Not suitable for H2 — the UPSERT logic depends on
 * PostgreSQL's {@code ON CONFLICT} semantics.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
class InvoiceSequenceRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private InvoiceSequenceRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InvoiceSequenceRepository(jdbcClient);
        // Insert test stores — OVERRIDING SYSTEM VALUE because m_store.id is GENERATED ALWAYS AS IDENTITY
        jdbcClient.sql("""
                INSERT INTO m_store (id, name) OVERRIDING SYSTEM VALUE VALUES (1, 'Test Store 1')
                ON CONFLICT (id) DO NOTHING
                """).update();
        jdbcClient.sql("""
                INSERT INTO m_store (id, name) OVERRIDING SYSTEM VALUE VALUES (2, 'Test Store 2')
                ON CONFLICT (id) DO NOTHING
                """).update();
    }

    @AfterEach
    void tearDown() {
        jdbcClient.sql("DELETE FROM sys_invoice_sequence").update();
        jdbcClient.sql("DELETE FROM m_store WHERE id IN (1, 2)").update();
    }

    @Test
    void getNextInvoiceSequence_sequentialIncrements() {
        LocalDate date = LocalDate.of(2026, 6, 13);

        int seq1 = repository.getNextInvoiceSequence(1, date);
        int seq2 = repository.getNextInvoiceSequence(1, date);
        int seq3 = repository.getNextInvoiceSequence(1, date);
        int seq4 = repository.getNextInvoiceSequence(1, date);
        int seq5 = repository.getNextInvoiceSequence(1, date);

        assertThat(seq1).isEqualTo(1);
        assertThat(seq2).isEqualTo(2);
        assertThat(seq3).isEqualTo(3);
        assertThat(seq4).isEqualTo(4);
        assertThat(seq5).isEqualTo(5);
    }

    @Test
    void getNextInvoiceSequence_concurrentAccess_allDistinct() throws Exception {
        int numThreads = 10;
        Set<Integer> results = ConcurrentHashMap.newKeySet();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    int seq = repository.getNextInvoiceSequence(1, LocalDate.of(2026, 6, 13));
                    results.add(seq);
                    return null;
                });
            }
        }

        assertThat(results).hasSize(numThreads);
        // All numbers 1..N must be present — proves atomic UPSERT with no gaps or duplicates
        for (int expected = 1; expected <= numThreads; expected++) {
            assertThat(results).contains(expected);
        }
    }

    @Test
    void getNextInvoiceSequence_differentDates_resetsCounter() {
        LocalDate date1 = LocalDate.of(2026, 6, 13);
        LocalDate date2 = LocalDate.of(2026, 6, 14);

        int seqD1Call1 = repository.getNextInvoiceSequence(1, date1);
        int seqD1Call2 = repository.getNextInvoiceSequence(1, date1);

        int seqD2Call1 = repository.getNextInvoiceSequence(1, date2);
        int seqD2Call2 = repository.getNextInvoiceSequence(1, date2);

        assertThat(seqD1Call1).isEqualTo(1);
        assertThat(seqD1Call2).isEqualTo(2);
        // Date 2 counter resets — starts from 1
        assertThat(seqD2Call1).isEqualTo(1);
        assertThat(seqD2Call2).isEqualTo(2);
    }

    @Test
    void getNextInvoiceSequence_differentStores_independent() {
        LocalDate date = LocalDate.of(2026, 6, 13);

        int store1Seq1 = repository.getNextInvoiceSequence(1, date);
        int store2Seq1 = repository.getNextInvoiceSequence(2, date);
        int store1Seq2 = repository.getNextInvoiceSequence(1, date);
        int store2Seq2 = repository.getNextInvoiceSequence(2, date);

        // Store 1: 1, 2
        assertThat(store1Seq1).isEqualTo(1);
        assertThat(store1Seq2).isEqualTo(2);
        // Store 2: 1, 2 (independent from store 1)
        assertThat(store2Seq1).isEqualTo(1);
        assertThat(store2Seq2).isEqualTo(2);
    }

    @Test
    void getNextInvoiceSequence_transactionRollback_cancelsIncrement() {
        LocalDate date = LocalDate.of(2026, 6, 13);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // Increment inside a transaction that we force to roll back — the increment must not persist.
        int seqInRolledBackTx = tx.execute(status -> {
            int seq = repository.getNextInvoiceSequence(1, date);
            status.setRollbackOnly();
            return seq;
        });
        assertThat(seqInRolledBackTx).isEqualTo(1);

        // After rollback, the very next call must still return 1 — proving gapless semantics:
        // a cancelled checkout burns no invoice number.
        int seqAfterRollback = repository.getNextInvoiceSequence(1, date);
        assertThat(seqAfterRollback).isEqualTo(1);
    }

    @Test
    void getNextInvoiceSequence_nonExistentStore_rejectedByForeignKey() {
        LocalDate date = LocalDate.of(2026, 6, 13);

        // Store 99999 does not exist in m_store; the FK constraint must reject the insert.
        assertThatThrownBy(() -> repository.getNextInvoiceSequence(99999, date))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void getNextInvoiceSequence_incrementingOneStore_leavesOtherStoreUntouched() {
        LocalDate date = LocalDate.of(2026, 6, 13);

        // Hammer store 1; store 2 must have no row at all (its counter never starts).
        for (int i = 0; i < 5; i++) {
            repository.getNextInvoiceSequence(1, date);
        }

        Optional<Integer> store2Seq = jdbcClient.sql("""
                        SELECT last_seq FROM sys_invoice_sequence WHERE store_id = 2 AND seq_date = :date
                        """)
                .param("date", date)
                .query(Integer.class)
                .optional();

        assertThat(store2Seq).isEmpty();
    }
}
