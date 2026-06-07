package id.my.mfikriproject.widuri.erp.modules.inventory.service;

import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.StockAdjustRequest;

public interface StockAdjustmentService {
    ProductResponse adjustIn(Long productId, StockAdjustRequest request);
    ProductResponse adjustOut(Long productId, StockAdjustRequest request);
}
