package id.my.mfikriproject.widuri.erp.modules.inventory;

import id.my.mfikriproject.widuri.erp.core.exception.DuplicateEntityException;
import id.my.mfikriproject.widuri.erp.core.exception.EntityNotFoundException;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupRequest;
import id.my.mfikriproject.widuri.erp.modules.inventory.dto.ProductGroupResponse;
import id.my.mfikriproject.widuri.erp.modules.inventory.entity.ProductGroupModel;
import id.my.mfikriproject.widuri.erp.modules.inventory.repository.ProductGroupRepository;
import id.my.mfikriproject.widuri.erp.modules.inventory.service.impl.ProductGroupServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ProductGroupServiceTest {

    @Mock
    private ProductGroupRepository repository;

    @InjectMocks
    private ProductGroupServiceImpl service;

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

    @Test
    void findById_found_returnsCorrectResponse() {
        ProductGroupModel model = mock(ProductGroupModel.class);
        given(model.getId()).willReturn(1L);
        given(model.getName()).willReturn("Joran Shimano");
        given(model.getBrand()).willReturn("Shimano");
        given(model.getCategory()).willReturn("Rod");
        given(model.getDescription()).willReturn("Joran spinning Shimano");
        given(model.getCreatedAt()).willReturn(null);
        given(model.getUpdatedAt()).willReturn(null);
        given(repository.findById(1L)).willReturn(Optional.of(model));

        ProductGroupResponse result = service.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Joran Shimano");
        assertThat(result.brand()).isEqualTo("Shimano");
        assertThat(result.category()).isEqualTo("Rod");
        assertThat(result.description()).isEqualTo("Joran spinning Shimano");
    }

    @Test
    void findById_delegatesToRepositoryWithId() {
        ProductGroupModel model = mock(ProductGroupModel.class);
        given(model.getCreatedAt()).willReturn(null);
        given(model.getUpdatedAt()).willReturn(null);
        given(repository.findById(42L)).willReturn(Optional.of(model));

        service.findById(42L);

        verify(repository).findById(42L);
    }

    @Test
    void findById_notFound_throwsEntityNotFoundException() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void create_withBrand_returnsResponseWithAllFields() {
        ProductGroupRequest request = new ProductGroupRequest("Reel Spinning", "Shimano", "Reel", "Deskripsi");
        given(repository.existsByNameAndBrand("Reel Spinning", "Shimano")).willReturn(false);
        ProductGroupModel saved = mock(ProductGroupModel.class);
        given(saved.getId()).willReturn(1L);
        given(saved.getName()).willReturn("Reel Spinning");
        given(saved.getBrand()).willReturn("Shimano");
        given(saved.getCategory()).willReturn("Reel");
        given(saved.getDescription()).willReturn("Deskripsi");
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(repository.save(any())).willReturn(saved);

        ProductGroupResponse result = service.create(request);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Reel Spinning");
        assertThat(result.brand()).isEqualTo("Shimano");
        assertThat(result.category()).isEqualTo("Reel");
        assertThat(result.description()).isEqualTo("Deskripsi");
    }

    @Test
    void create_withNullBrand_returnsResponseWithNullBrand() {
        ProductGroupRequest request = new ProductGroupRequest("Reel Spinning", null, "Reel", null);
        given(repository.existsByNameAndBrandIsNull("Reel Spinning")).willReturn(false);
        ProductGroupModel saved = mock(ProductGroupModel.class);
        given(saved.getId()).willReturn(2L);
        given(saved.getName()).willReturn("Reel Spinning");
        given(saved.getBrand()).willReturn(null);
        given(saved.getCategory()).willReturn("Reel");
        given(saved.getDescription()).willReturn(null);
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(repository.save(any())).willReturn(saved);

        ProductGroupResponse result = service.create(request);

        assertThat(result.name()).isEqualTo("Reel Spinning");
        assertThat(result.brand()).isNull();
    }

    @Test
    void create_noDuplicate_persistsEntityWithCorrectFields() {
        ProductGroupRequest request = new ProductGroupRequest("Reel Spinning", "Shimano", "Reel", "Desc");
        given(repository.existsByNameAndBrand("Reel Spinning", "Shimano")).willReturn(false);
        ProductGroupModel saved = mock(ProductGroupModel.class);
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(repository.save(any())).willReturn(saved);

        service.create(request);

        ArgumentCaptor<ProductGroupModel> captor = ArgumentCaptor.forClass(ProductGroupModel.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Reel Spinning");
        assertThat(captor.getValue().getBrand()).isEqualTo("Shimano");
        assertThat(captor.getValue().getCategory()).isEqualTo("Reel");
        assertThat(captor.getValue().getDescription()).isEqualTo("Desc");
    }

    @Test
    void create_withBrand_usesNameAndBrandExistsCheck() {
        ProductGroupRequest request = new ProductGroupRequest("Reel Spinning", "Shimano", null, null);
        given(repository.existsByNameAndBrand("Reel Spinning", "Shimano")).willReturn(false);
        ProductGroupModel saved = mock(ProductGroupModel.class);
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(repository.save(any())).willReturn(saved);

        service.create(request);

        verify(repository).existsByNameAndBrand("Reel Spinning", "Shimano");
        verify(repository, never()).existsByNameAndBrandIsNull(any());
    }

    @Test
    void create_withNullBrand_usesNullBrandExistsCheck() {
        ProductGroupRequest request = new ProductGroupRequest("Reel Spinning", null, null, null);
        given(repository.existsByNameAndBrandIsNull("Reel Spinning")).willReturn(false);
        ProductGroupModel saved = mock(ProductGroupModel.class);
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(repository.save(any())).willReturn(saved);

        service.create(request);

        verify(repository).existsByNameAndBrandIsNull("Reel Spinning");
        verify(repository, never()).existsByNameAndBrand(any(), any());
    }

    @Test
    void create_duplicateNameAndBrand_throwsDuplicateEntityException() {
        ProductGroupRequest request = new ProductGroupRequest("Reel Spinning", "Shimano", null, null);
        given(repository.existsByNameAndBrand("Reel Spinning", "Shimano")).willReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateEntityException.class);
    }

    @Test
    void create_duplicateNameWithNullBrand_throwsDuplicateEntityException() {
        ProductGroupRequest request = new ProductGroupRequest("Reel Spinning", null, null, null);
        given(repository.existsByNameAndBrandIsNull("Reel Spinning")).willReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateEntityException.class);
    }

    @Test
    void create_concurrentDuplicate_throwsDuplicateEntityException() {
        ProductGroupRequest request = new ProductGroupRequest("Reel Spinning", "Shimano", null, null);
        given(repository.existsByNameAndBrand("Reel Spinning", "Shimano")).willReturn(false);
        given(repository.save(any())).willThrow(new DataIntegrityViolationException("unique constraint"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateEntityException.class);
    }

    @Test
    void update_found_withBrand_returnsUpdatedResponse() {
        ProductGroupModel entity = mock(ProductGroupModel.class);
        given(repository.findById(1L)).willReturn(Optional.of(entity));
        given(repository.existsByNameAndBrandAndIdNot("Reel Baru", "Shimano", 1L)).willReturn(false);
        ProductGroupModel saved = mock(ProductGroupModel.class);
        given(saved.getId()).willReturn(1L);
        given(saved.getName()).willReturn("Reel Baru");
        given(saved.getBrand()).willReturn("Shimano");
        given(saved.getCategory()).willReturn("Reel");
        given(saved.getDescription()).willReturn("Desc baru");
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(repository.save(entity)).willReturn(saved);

        ProductGroupResponse result = service.update(1L, new ProductGroupRequest("Reel Baru", "Shimano", "Reel", "Desc baru"));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Reel Baru");
        assertThat(result.brand()).isEqualTo("Shimano");
        assertThat(result.category()).isEqualTo("Reel");
        assertThat(result.description()).isEqualTo("Desc baru");
    }

    @Test
    void update_found_withNullBrand_returnsUpdatedResponse() {
        ProductGroupModel entity = mock(ProductGroupModel.class);
        given(repository.findById(2L)).willReturn(Optional.of(entity));
        given(repository.existsByNameAndBrandIsNullAndIdNot("Reel Baru", 2L)).willReturn(false);
        ProductGroupModel saved = mock(ProductGroupModel.class);
        given(saved.getId()).willReturn(2L);
        given(saved.getName()).willReturn("Reel Baru");
        given(saved.getBrand()).willReturn(null);
        given(saved.getCategory()).willReturn(null);
        given(saved.getDescription()).willReturn(null);
        given(saved.getCreatedAt()).willReturn(null);
        given(saved.getUpdatedAt()).willReturn(null);
        given(repository.save(entity)).willReturn(saved);

        ProductGroupResponse result = service.update(2L, new ProductGroupRequest("Reel Baru", null, null, null));

        assertThat(result.name()).isEqualTo("Reel Baru");
        assertThat(result.brand()).isNull();
    }

    @Test
    void update_notFound_throwsEntityNotFoundException() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new ProductGroupRequest("X", null, null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_duplicateNameAndBrand_throwsDuplicateEntityException() {
        ProductGroupModel entity = mock(ProductGroupModel.class);
        given(repository.findById(1L)).willReturn(Optional.of(entity));
        given(repository.existsByNameAndBrandAndIdNot("Reel Spinning", "Shimano", 1L)).willReturn(true);

        assertThatThrownBy(() -> service.update(1L, new ProductGroupRequest("Reel Spinning", "Shimano", null, null)))
                .isInstanceOf(DuplicateEntityException.class);
    }

    @Test
    void update_duplicateNameWithNullBrand_throwsDuplicateEntityException() {
        ProductGroupModel entity = mock(ProductGroupModel.class);
        given(repository.findById(1L)).willReturn(Optional.of(entity));
        given(repository.existsByNameAndBrandIsNullAndIdNot("Reel Spinning", 1L)).willReturn(true);

        assertThatThrownBy(() -> service.update(1L, new ProductGroupRequest("Reel Spinning", null, null, null)))
                .isInstanceOf(DuplicateEntityException.class);
    }

    @Test
    void update_concurrentDuplicate_throwsDuplicateEntityException() {
        ProductGroupModel entity = mock(ProductGroupModel.class);
        given(repository.findById(1L)).willReturn(Optional.of(entity));
        given(repository.existsByNameAndBrandAndIdNot("Reel Spinning", "Shimano", 1L)).willReturn(false);
        given(repository.save(entity)).willThrow(new DataIntegrityViolationException("unique constraint"));

        assertThatThrownBy(() -> service.update(1L, new ProductGroupRequest("Reel Spinning", "Shimano", null, null)))
                .isInstanceOf(DuplicateEntityException.class);
    }

    @Test
    void update_persistsCorrectFields() {
        ProductGroupModel entity = ProductGroupModel.builder()
                .name("Old Name")
                .brand("Old Brand")
                .category("Old Cat")
                .description("Old Desc")
                .build();
        given(repository.findById(1L)).willReturn(Optional.of(entity));
        given(repository.existsByNameAndBrandAndIdNot("New Name", "New Brand", 1L)).willReturn(false);
        given(repository.save(entity)).willReturn(entity);

        service.update(1L, new ProductGroupRequest("New Name", "New Brand", "New Cat", "New Desc"));

        ArgumentCaptor<ProductGroupModel> captor = ArgumentCaptor.forClass(ProductGroupModel.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("New Name");
        assertThat(captor.getValue().getBrand()).isEqualTo("New Brand");
        assertThat(captor.getValue().getCategory()).isEqualTo("New Cat");
        assertThat(captor.getValue().getDescription()).isEqualTo("New Desc");
    }

    @Test
    void delete_existingId_callsDelete() {
        ProductGroupModel entity = mock(ProductGroupModel.class);
        given(repository.findById(1L)).willReturn(Optional.of(entity));

        service.delete(1L);

        verify(repository).delete(entity);
    }

    @Test
    void delete_nonExistentId_throwsEntityNotFoundException() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

}
