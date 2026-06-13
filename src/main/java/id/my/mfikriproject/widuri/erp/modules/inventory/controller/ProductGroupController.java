package id.my.mfikriproject.widuri.erp.modules.inventory.controller;

import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.ProductGroupService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{id}")
    public ResponseEntity<ProductGroupResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productGroupService.findById(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductGroupResponse> create(
            @RequestBody @Valid ProductGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productGroupService.create(request));
    }

    @PutMapping(value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductGroupResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid ProductGroupRequest request
    ) {
        return ResponseEntity.ok(productGroupService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
