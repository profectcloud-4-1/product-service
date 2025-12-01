package profect.group1.goormdotcom.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.StockClient;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.dto.StockResponseDto;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.service.ProductListItemQueryService;
import profect.group1.goormdotcom.product.service.utils.ImageUrlGenerator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductListItemQueryService bulk 테스트")
class ProductListItemQueryServiceTest {

    @InjectMocks
    private ProductListItemQueryService service;

    @Mock private ProductRepository productRepository;
    @Mock private StockClient stockClient;
    @Mock private ImageUrlGenerator imageUrlGenerator;

    private ProductEntity entity(UUID id, String name, int price, UUID mainImageId) {
        return new ProductEntity(id, UUID.randomUUID(), UUID.randomUUID(), name, price, mainImageId, "desc");
    }

    @Test
    @DisplayName("모든 엔티티 존재 + 재고 응답: 순서 유지, 상태 매핑(가용/품절)")
    void bulk_allExist_withStockResult_mapsStatusesAndOrder() {
        // given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID img1 = UUID.randomUUID();
        UUID img2 = UUID.randomUUID();

        List<UUID> ids = List.of(id1, id2);

        ProductEntity e1 = entity(id1, "A", 1000, img1);
        ProductEntity e2 = entity(id2, "B", 2000, img2);
        when(productRepository.findAllByIdIncludingDeleted(ids)).thenReturn(List.of(e1, e2));

        when(imageUrlGenerator.generateProductImageUrl(img1)).thenReturn("u1");
        when(imageUrlGenerator.generateProductImageUrl(img2)).thenReturn("u2");

        LocalDateTime now = LocalDateTime.now();
        when(stockClient.getStocksBulk(ids)).thenReturn(
                ApiResponse.onSuccess(List.of(
                        new StockResponseDto(id1, 3, now),
                        new StockResponseDto(id2, 0, now)
                ))
        );

        // when
        List<ProductListItem> result = service.getCartProductListItemsBulkFromOrigin(ids);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(id1);
        assertThat(result.get(0).getMainImageUrl()).isEqualTo("u1");
        assertThat(result.get(0).getStatus()).isEqualTo("AVAILABLE");

        assertThat(result.get(1).getId()).isEqualTo(id2);
        assertThat(result.get(1).getMainImageUrl()).isEqualTo("u2");
        assertThat(result.get(1).getStatus()).isEqualTo("SOLD_OUT");

        verify(productRepository, times(1)).findAllByIdIncludingDeleted(ids);
        verify(stockClient, times(1)).getStocksBulk(ids);
        verify(imageUrlGenerator, times(1)).generateProductImageUrl(img1);
        verify(imageUrlGenerator, times(1)).generateProductImageUrl(img2);
        verify(imageUrlGenerator, never()).generateDefaultImageUrl();
    }

    @Test
    @DisplayName("단건: deleted_at 존재 시 NOT_EXIST로 반환하고 재고 미조회")
    void single_deletedAt_returnsNotExist_withoutStockCall() {
        // given
        UUID id = UUID.randomUUID();
        UUID brand = UUID.randomUUID();
        UUID cat = UUID.randomUUID();
        UUID main = UUID.randomUUID();

        // All-args 생성자 순서: id, brandId, categoryId, name, price, description, mainImageId, createdAt, deletedAt
        var deletedAt = java.time.LocalDateTime.now();
        var createdAt = deletedAt.minusDays(1);
        ProductEntity deleted = new ProductEntity(id, brand, cat, "Del", 999, "desc", main, createdAt, deletedAt);

        when(productRepository.findByIdIncludingDeleted(id)).thenReturn(java.util.Optional.of(deleted));
        when(imageUrlGenerator.generateProductImageUrl(main)).thenReturn("uDel");

        // when
        ProductListItem item = service.getCartProductListItemFromOrigin(id);

        // then
        assertThat(item.getId()).isEqualTo(id);
        assertThat(item.getMainImageUrl()).isEqualTo("uDel");
        assertThat(item.getStatus()).isEqualTo("NOT_EXIST");
        verify(stockClient, never()).getStock(any());
    }

    @Test
    @DisplayName("다건: deleted_at 존재 시 NOT_EXIST로 매핑(재고 무관)")
    void bulk_deletedAt_mapsNotExist_evenIfStockAvailable() {
        // given
        UUID id = UUID.randomUUID();
        UUID brand = UUID.randomUUID();
        UUID cat = UUID.randomUUID();
        UUID main = UUID.randomUUID();
        var deletedAt = java.time.LocalDateTime.now();
        var createdAt = deletedAt.minusDays(1);
        ProductEntity deleted = new ProductEntity(id, brand, cat, "Del", 1000, "desc", main, createdAt, deletedAt);

        when(productRepository.findAllByIdIncludingDeleted(List.of(id))).thenReturn(List.of(deleted));
        when(imageUrlGenerator.generateProductImageUrl(main)).thenReturn("uDel");

        // 재고는 있어도, 삭제된 상품이면 NOT_EXIST로 처리 기대
        when(stockClient.getStocksBulk(List.of(id))).thenReturn(
                ApiResponse.onSuccess(List.of(new StockResponseDto(id, 5, java.time.LocalDateTime.now())))
        );

        // when
        List<ProductListItem> items = service.getCartProductListItemsBulkFromOrigin(List.of(id));

        // then
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getId()).isEqualTo(id);
        assertThat(items.get(0).getMainImageUrl()).isEqualTo("uDel");
        assertThat(items.get(0).getStatus()).isEqualTo("NOT_EXIST");
    }

    @Test
    @DisplayName("재고 응답 null: 엔티티 기반으로 NOT_EXIST 매핑")
    void bulk_stockResultNull_mapsNotExistForAll() {
        // given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID img1 = UUID.randomUUID();
        UUID img2 = UUID.randomUUID();
        List<UUID> ids = List.of(id1, id2);

        ProductEntity e1 = entity(id1, "A", 1000, img1);
        ProductEntity e2 = entity(id2, "B", 2000, img2);
        when(productRepository.findAllByIdIncludingDeleted(ids)).thenReturn(List.of(e1, e2));

        when(imageUrlGenerator.generateProductImageUrl(img1)).thenReturn("u1");
        when(imageUrlGenerator.generateProductImageUrl(img2)).thenReturn("u2");

        // result=null 시뮬레이션
        when(stockClient.getStocksBulk(ids)).thenReturn(ApiResponse.onFailure("ERR", "stock error", null));

        // when
        List<ProductListItem> result = service.getCartProductListItemsBulkFromOrigin(ids);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(id1);
        assertThat(result.get(0).getMainImageUrl()).isEqualTo("u1");
        assertThat(result.get(0).getStatus()).isEqualTo("NOT_EXIST");

        assertThat(result.get(1).getId()).isEqualTo(id2);
        assertThat(result.get(1).getMainImageUrl()).isEqualTo("u2");
        assertThat(result.get(1).getStatus()).isEqualTo("NOT_EXIST");

        verify(imageUrlGenerator, times(1)).generateProductImageUrl(img1);
        verify(imageUrlGenerator, times(1)).generateProductImageUrl(img2);
        verify(imageUrlGenerator, never()).generateDefaultImageUrl();
    }

    @Test
    @DisplayName("존재하지 않는 상품 ID는 기본 이미지 + NOT_EXIST, 입력 순서 유지")
    void bulk_missingEntity_addedWithDefaultImage_notExist_andOrderKept() {
        // given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID img1 = UUID.randomUUID();
        UUID img2 = UUID.randomUUID();
        List<UUID> ids = List.of(id1, missing, id2);

        ProductEntity e1 = entity(id1, "A", 1000, img1);
        ProductEntity e2 = entity(id2, "B", 2000, img2);
        // DB에는 id1, id2만 존재
        when(productRepository.findAllByIdIncludingDeleted(ids)).thenReturn(List.of(e1, e2));

        when(imageUrlGenerator.generateProductImageUrl(img1)).thenReturn("u1");
        when(imageUrlGenerator.generateProductImageUrl(img2)).thenReturn("u2");
        when(imageUrlGenerator.generateDefaultImageUrl()).thenReturn("udef");

        LocalDateTime now = LocalDateTime.now();
        // 재고에는 존재하는 항목만 내려온다고 가정
        when(stockClient.getStocksBulk(ids)).thenReturn(
                ApiResponse.onSuccess(List.of(
                        new StockResponseDto(id1, 2, now),
                        new StockResponseDto(id2, 0, now)
                ))
        );

        // when
        List<ProductListItem> result = service.getCartProductListItemsBulkFromOrigin(ids);

        // then: 입력 순서 유지 [id1, missing, id2]
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(id1);
        assertThat(result.get(0).getStatus()).isEqualTo("AVAILABLE");
        assertThat(result.get(0).getMainImageUrl()).isEqualTo("u1");

        assertThat(result.get(1).getId()).isEqualTo(missing);
        assertThat(result.get(1).getStatus()).isEqualTo("NOT_EXIST");
        assertThat(result.get(1).getMainImageUrl()).isEqualTo("udef");

        assertThat(result.get(2).getId()).isEqualTo(id2);
        assertThat(result.get(2).getStatus()).isEqualTo("SOLD_OUT");
        assertThat(result.get(2).getMainImageUrl()).isEqualTo("u2");

        verify(productRepository, times(1)).findAllByIdIncludingDeleted(ids);
        verify(stockClient, times(1)).getStocksBulk(ids);
        verify(imageUrlGenerator, times(1)).generateDefaultImageUrl();
    }
}
