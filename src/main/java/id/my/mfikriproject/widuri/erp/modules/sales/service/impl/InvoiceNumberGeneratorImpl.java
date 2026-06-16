package id.my.mfikriproject.widuri.erp.modules.sales.service.impl;

import id.my.mfikriproject.widuri.erp.modules.sales.repository.InvoiceSequenceRepository;
import id.my.mfikriproject.widuri.erp.modules.sales.service.InvoiceNumberGenerator;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class InvoiceNumberGeneratorImpl implements InvoiceNumberGenerator {
    private final InvoiceSequenceRepository invoiceSequenceRepository;

    public InvoiceNumberGeneratorImpl(InvoiceSequenceRepository invoiceSequenceRepository) {
        this.invoiceSequenceRepository = invoiceSequenceRepository;
    }

    @Override
    public String generate(Integer storeId, LocalDate date) {
        if (storeId == null || storeId <= 0 || date == null) {
            throw new IllegalArgumentException("storeId must be a positive integer and date must not be null");
        }

        int sequence = invoiceSequenceRepository.getNextInvoiceSequence(storeId, date);

        return "INV-%02d-%s-%04d".formatted(storeId, date.format(DateTimeFormatter.BASIC_ISO_DATE), sequence);
    }
}
