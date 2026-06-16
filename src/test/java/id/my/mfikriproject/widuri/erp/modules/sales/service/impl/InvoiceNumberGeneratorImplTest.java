package id.my.mfikriproject.widuri.erp.modules.sales.service.impl;

import id.my.mfikriproject.widuri.erp.modules.sales.repository.InvoiceSequenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InvoiceNumberGeneratorImplTest {

    @Mock
    private InvoiceSequenceRepository invoiceSequenceRepository;

    @InjectMocks
    private InvoiceNumberGeneratorImpl generator;

    @Test
    void generate_cleanInputs_returnsCorrectFormat() {
        given(invoiceSequenceRepository.getNextInvoiceSequence(2, LocalDate.of(2026, 6, 13)))
                .willReturn(42);

        String result = generator.generate(2, LocalDate.of(2026, 6, 13));

        assertThat(result).isEqualTo("INV-02-20260613-0042");
    }

    @Test
    void generate_storeIdIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> generator.generate(null, LocalDate.of(2026, 6, 13)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_storeIdIsZero_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> generator.generate(0, LocalDate.of(2026, 6, 13)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_storeIdIsNegative_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> generator.generate(-1, LocalDate.of(2026, 6, 13)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_dateIsNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> generator.generate(1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generate_sequenceOne_padsToFourDigits() {
        given(invoiceSequenceRepository.getNextInvoiceSequence(1, LocalDate.of(2026, 6, 13)))
                .willReturn(1);

        String result = generator.generate(1, LocalDate.of(2026, 6, 13));

        assertThat(result).isEqualTo("INV-01-20260613-0001");
    }

    @Test
    void generate_sequenceLessThanFourDigits_padsWithZeros() {
        given(invoiceSequenceRepository.getNextInvoiceSequence(1, LocalDate.of(2026, 6, 13)))
                .willReturn(99);

        String result = generator.generate(1, LocalDate.of(2026, 6, 13));

        assertThat(result).isEqualTo("INV-01-20260613-0099");
    }

    @Test
    void generate_largeSequence_rendersWithoutTruncation() {
        given(invoiceSequenceRepository.getNextInvoiceSequence(1, LocalDate.of(2026, 6, 13)))
                .willReturn(12345);

        String result = generator.generate(1, LocalDate.of(2026, 6, 13));

        assertThat(result).isEqualTo("INV-01-20260613-12345");
    }

    @Test
    void generate_delegatesToRepository() {
        given(invoiceSequenceRepository.getNextInvoiceSequence(1, LocalDate.of(2026, 6, 13)))
                .willReturn(5);

        generator.generate(1, LocalDate.of(2026, 6, 13));

        verify(invoiceSequenceRepository).getNextInvoiceSequence(1, LocalDate.of(2026, 6, 13));
    }
}
