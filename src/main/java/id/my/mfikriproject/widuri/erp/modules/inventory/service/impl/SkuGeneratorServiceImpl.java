package id.my.mfikriproject.widuri.erp.modules.inventory.service.impl;

import id.my.mfikriproject.widuri.erp.core.exception.BusinessRuleException;
import id.my.mfikriproject.widuri.erp.modules.inventory.repository.SkuRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.SkuGeneratorService;
import org.springframework.stereotype.Service;

@Service
public class SkuGeneratorServiceImpl implements SkuGeneratorService {
    private final SkuRepository skuRepository;

    public SkuGeneratorServiceImpl(SkuRepository skuRepository) {
        this.skuRepository = skuRepository;
    }

    @Override
    public String generate(String brand, String category, String attribute) {
        String normalizedBrand = normalize(brand);
        String normalizedCategory = normalize(category);
        String normalizedAttribute = normalize(attribute);

        String seq = skuRepository.getNextSkuSequence();
        return normalizedBrand + "-" + normalizedCategory + "-" + normalizedAttribute + "-" + seq;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("SKU component must not be null or blank");
        }
        return value.trim().toUpperCase().replaceAll("\\s+", "-");
    }
}
