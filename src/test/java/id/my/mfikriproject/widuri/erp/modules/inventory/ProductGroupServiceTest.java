package id.my.mfikriproject.widuri.erp.modules.inventory;

import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductGroupModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.repository.ProductGroupRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.ProductGroupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductGroupServiceTest {

    @Mock
    private ProductGroupRepository repository;

    @InjectMocks
    private ProductGroupService service;

    @Test
    void findAll_delegatesToRepositoryWithSamePageable() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name"));
        given(repository.findAll(pageable)).willReturn(Page.empty(pageable));

        service.findAll(pageable);

        verify(repository).findAll(pageable);
    }

    @Test
    void findAll_mapsEntitiesToResponse() {
        Pageable pageable = PageRequest.of(0, 20);
        ProductGroupModel model = mock(ProductGroupModel.class);
        given(model.getId()).willReturn(1L);
        given(model.getName()).willReturn("Joran Shimano");
        given(model.getBrand()).willReturn("Shimano");
        given(model.getCategory()).willReturn("Rod");
        given(model.getDescription()).willReturn(null);
        given(model.getCreatedAt()).willReturn(null);
        given(model.getUpdatedAt()).willReturn(null);
        given(repository.findAll(pageable)).willReturn(new PageImpl<>(List.of(model), pageable, 1));

        Page<ProductGroupResponse> result = service.findAll(pageable);

        assertThat(result.getContent()).hasSize(1);
        ProductGroupResponse response = result.getContent().getFirst();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Joran Shimano");
        assertThat(response.brand()).isEqualTo("Shimano");
        assertThat(response.category()).isEqualTo("Rod");
    }

    @Test
    void findAll_emptyRepository_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 20);
        given(repository.findAll(pageable)).willReturn(Page.empty(pageable));

        Page<ProductGroupResponse> result = service.findAll(pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void findAll_preservesPaginationMetadata() {
        Pageable pageable = PageRequest.of(2, 5);
        given(repository.findAll(pageable)).willReturn(new PageImpl<>(List.of(), pageable, 30));

        Page<ProductGroupResponse> result = service.findAll(pageable);

        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotalElements()).isEqualTo(30);
        assertThat(result.getTotalPages()).isEqualTo(6);
    }
}
