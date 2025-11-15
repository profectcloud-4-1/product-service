package profect.group1.goormdotcom.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.service.ProductListItemCacheService;
import profect.group1.goormdotcom.product.service.ProductListItemService;
import profect.group1.goormdotcom.product.service.utils.ImageUrlGenerator;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductListItemService 테스트")
class ProductListItemServiceTest {

    @InjectMocks
    private ProductListItemService productListItemService;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ImageUrlGenerator imageUrlGenerator;
    @Mock
    private ProductListItemCacheService productSummaryCacheService;

    private ProductEntity entity(UUID id, String name, int price, UUID mainImageId) {
        return new ProductEntity(id, UUID.randomUUID(), UUID.randomUUID(), name, price, mainImageId, "desc");
    }

    @Nested
    @DisplayName("getProducts 동작")
    class GetProducts {
        @Test
        @DisplayName("키워드가 없으면 findAll로 페이지 조회하고 매핑한다")
        void getProducts_NoKeyword_UsesFindAll() {
            // given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID img1 = UUID.randomUUID();
            UUID img2 = UUID.randomUUID();
            List<ProductEntity> entities = List.of(
                    entity(id1, "A", 1000, img1),
                    entity(id2, "B", 2000, img2)
            );

            Page<ProductEntity> page = new PageImpl<>(entities);
            when(productRepository.findAll(any(Pageable.class))).thenReturn(page);
            when(imageUrlGenerator.generateProductImageUrl(img1)).thenReturn("url1");
            when(imageUrlGenerator.generateProductImageUrl(img2)).thenReturn("url2");

            // when
            List<ProductListItem> result = productListItemService.getProducts(2, 5, "price", "asc", null);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(id1);
            assertThat(result.get(0).getMainImageUrl()).isEqualTo("url1");
            assertThat(result.get(0).getStatus()).isEqualTo("AVAILABLE");
            assertThat(result.get(1).getId()).isEqualTo(id2);
            assertThat(result.get(1).getMainImageUrl()).isEqualTo("url2");

            // Pageable 검증 (1-based page -> 0-based로 변환, 정렬 적용)
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(productRepository).findAll(captor.capture());
            Pageable used = captor.getValue();
            assertThat(used.getPageNumber()).isEqualTo(1); // 2 -> 1
            assertThat(used.getPageSize()).isEqualTo(5);
            assertThat(used.getSort().toString()).containsIgnoringCase("price");
            assertThat(used.getSort().toString()).containsIgnoringCase("ASC");

            verify(imageUrlGenerator, times(2)).generateProductImageUrl(any(UUID.class));
        }

        @Test
        @DisplayName("키워드가 있으면 findByNameContainingIgnoreCase로 조회한다")
        void getProducts_WithKeyword_UsesSearch() {
            // given
            String keyword = "phone";
            UUID id = UUID.randomUUID();
            UUID img = UUID.randomUUID();
            List<ProductEntity> entities = List.of(entity(id, "phone X", 3000, img));
            Page<ProductEntity> page = new PageImpl<>(entities);
            when(productRepository.findByNameContainingIgnoreCase(eq(keyword), any(Pageable.class))).thenReturn(page);
            when(imageUrlGenerator.generateProductImageUrl(img)).thenReturn("urlX");

            // when
            List<ProductListItem> result = productListItemService.getProducts(1, 10, "createdAt", "desc", keyword);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(id);
            assertThat(result.get(0).getMainImageUrl()).isEqualTo("urlX");
            assertThat(result.get(0).getStatus()).isEqualTo("AVAILABLE");

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(productRepository).findByNameContainingIgnoreCase(eq(keyword), captor.capture());
            Pageable used = captor.getValue();
            assertThat(used.getPageNumber()).isEqualTo(0); // 1 -> 0
            assertThat(used.getPageSize()).isEqualTo(10);
            assertThat(used.getSort().toString()).containsIgnoringCase("createdAt");
            assertThat(used.getSort().toString()).containsIgnoringCase("DESC");
        }
    }

    @Nested
    @DisplayName("getCartProducts 동작")
    class GetCartProducts {
        @Test
        @DisplayName("입력 ID 순서를 유지해 캐시에서 조회한다")
        void getCartProducts_DelegatesToCache_InOrder() {
            // given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            ProductListItem i1 = new ProductListItem(id1, "A", 1000, "u1", "AVAILABLE");
            ProductListItem i2 = new ProductListItem(id2, "B", 2000, "u2", "SOLD_OUT");

            when(productSummaryCacheService.getCartProductListItem(id1)).thenReturn(i1);
            when(productSummaryCacheService.getCartProductListItem(id2)).thenReturn(i2);

            // when
            List<ProductListItem> result = productListItemService.getCartProducts(List.of(id1, id2));

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(id1);
            assertThat(result.get(1).getId()).isEqualTo(id2);
            verify(productSummaryCacheService).getCartProductListItem(id1);
            verify(productSummaryCacheService).getCartProductListItem(id2);
            verifyNoInteractions(productRepository, imageUrlGenerator);
        }
    }
}
