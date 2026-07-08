package id.my.mfikriproject.widuri.erp.modules.sales.service;

import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutRequest;
import id.my.mfikriproject.widuri.erp.modules.sales.dto.CheckoutResponse;

public interface SalesService {
    CheckoutResponse checkout(CheckoutRequest request);
}
