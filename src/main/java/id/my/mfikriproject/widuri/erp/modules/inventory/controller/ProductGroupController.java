package id.my.mfikriproject.widuri.erp.modules.inventory.controller;

import id.my.mfikriproject.widuri.erp.modules.inventory.ProductGroupService;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api-path-prefix}product-groups")
public class ProductGroupController {

    private final ProductGroupService productGroupService;

    public ProductGroupController(ProductGroupService productGroupService) {
        this.productGroupService = productGroupService;
    }

    @GetMapping
    public ResponseEntity<Page<ProductGroupResponse>> findAll(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(productGroupService.findAll(pageable));
    }
}
