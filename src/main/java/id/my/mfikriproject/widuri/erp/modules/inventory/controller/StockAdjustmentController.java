package id.my.mfikriproject.widuri.erp.modules.inventory.controller;

import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.StockAdjustRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.StockAdjustmentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${app.api-path-prefix}products/{id}/stock")
public class StockAdjustmentController {
    private final StockAdjustmentService stockAdjustmentService;

    public StockAdjustmentController(StockAdjustmentService stockAdjustmentService) {
        this.stockAdjustmentService = stockAdjustmentService;
    }

    @PostMapping(value = "/in",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> adjustIn(
            @PathVariable Long id,
            @RequestBody @Valid StockAdjustRequest request
    ) {
        return ResponseEntity.ok(stockAdjustmentService.adjustIn(id, request));
    }

    @PostMapping(value = "/out",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductResponse> adjustOut(
            @PathVariable Long id,
            @RequestBody @Valid StockAdjustRequest request
    ) {
        return ResponseEntity.ok(stockAdjustmentService.adjustOut(id, request));
    }
}
