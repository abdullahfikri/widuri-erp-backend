package id.my.mfikriproject.widuri.erp.modules.sales.controller;

import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutRequest;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.SalesDetailResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.SalesSummaryResponse;
import id.my.mfikriproject.widuri.erp.modules.sales.service.SalesService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("${app.api-path-prefix}sales")
public class SalesController {

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    /**
     * POS checkout — creates a sales transaction with stock deduction.
     */
    @PostMapping(value = "/checkout",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CheckoutResponse> checkout(
            @RequestBody @Valid CheckoutRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(salesService.checkout(request));
    }

    /**
     * Paginated transaction history for the current store.
     * Query params: {@code from}, {@code to} (ISO date, e.g. 2026-05-01),
     * {@code page}, {@code size} (default 20), {@code sort}.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Page<SalesSummaryResponse>> getHistory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(salesService.getHistory(from, to, pageable));
    }

    /**
     * Single transaction detail by invoice number, scoped to the current store.
     */
    @GetMapping(value = "/{invoiceNumber}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SalesDetailResponse> getByInvoiceNumber(
            @PathVariable String invoiceNumber) {
        return ResponseEntity.ok(salesService.getByInvoiceNumber(invoiceNumber));
    }
}
