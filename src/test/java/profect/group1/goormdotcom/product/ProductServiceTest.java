package profect.group1.goormdotcom.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.file.FileStorageManager;
import profect.group1.goormdotcom.product.domain.Product;
import profect.group1.goormdotcom.product.domain.ProductImage;
import profect.group1.goormdotcom.product.domain.ProductSummary;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.StockClient;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.dto.StockRequestDto;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.dto.StockResponseDto;
import profect.group1.goormdotcom.product.repository.ProductImageRepository;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.repository.entity.ProductImageEntity;
import profect.group1.goormdotcom.product.service.ProductService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService 비즈니스 로직 테스트")
public class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private StockClient stockClient;
    @Mock
    private FileStorageManager fileStorageManager;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(productService, "cloudfrontDomain", "https://test-domain.com/");
    }

    private void given_재고_등록에_성공한다() {
        StockResponseDto stockResponse = new StockResponseDto(UUID.randomUUID(), 100, LocalDateTime.now());
        ApiResponse<StockResponseDto> apiResponse = ApiResponse.onSuccess(stockResponse);
        when(stockClient.registerStock(any(StockRequestDto.class))).thenReturn(apiResponse);
    }

    private void given_재고_등록에_실패한다() {
        ApiResponse<StockResponseDto> apiResponse = ApiResponse.onSuccess(null);
        when(stockClient.registerStock(any(StockRequestDto.class))).thenReturn(apiResponse);
    }

    private ProductEntity given_제품이_존재한다(UUID productId) {
        ProductEntity productEntity = new ProductEntity(productId, UUID.randomUUID(), UUID.randomUUID(), "기존 제품", 100, UUID.randomUUID(), "기존 설명");
        when(productRepository.findById(productId)).thenReturn(Optional.of(productEntity));
        return productEntity;
    }

    private void given_제품이_존재하지_않는다(UUID productId) {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());
    }

    private List<ProductImageEntity> given_제품의_이미지가_존재한다(UUID productId) {
        List<ProductImageEntity> images = List.of(
                new ProductImageEntity(UUID.randomUUID(), productId),
                new ProductImageEntity(UUID.randomUUID(), productId)
        );
        when(productImageRepository.findByProductId(productId)).thenReturn(images);
        return images;
    }

    private void given_제품의_이미지가_존재하지_않는다(UUID productId) {
        when(productImageRepository.findByProductId(productId)).thenReturn(Collections.emptyList());
    }

    private void given_이미지_ObjectKey_조회에_성공한다(UUID imageId, String objectKey) {
        when(fileStorageManager.getObjectKey(imageId)).thenReturn(objectKey);
    }

    private void given_이미지_ObjectKey_조회에_실패한다(UUID imageId) {
        when(fileStorageManager.getObjectKey(imageId)).thenThrow(new IllegalArgumentException("파일을 찾을 수 없습니다: " + imageId));
    }


    @Nested
    @DisplayName("제품 생성 시나리오")
    class CreateProductTest {

        @Test
        @DisplayName("성공 - 모든 외부 연동이 성공하면 제품이 생성된다.")
        void createProduct_Success() {
            // given
            given_재고_등록에_성공한다();
            List<UUID> imageIds = List.of(UUID.randomUUID(), UUID.randomUUID());

            // when
            UUID productId = productService.createProduct(UUID.randomUUID(), UUID.randomUUID(), "신제품", 10000, 100, UUID.randomUUID(), "설명", imageIds);

            // then: 행동 검증
            assertThat(productId).isNotNull();
            verify(stockClient, times(1)).registerStock(any(StockRequestDto.class));
            verify(productRepository, times(1)).save(any(ProductEntity.class));
            verify(productImageRepository, times(1)).saveAll(any());
            verify(fileStorageManager, times(imageIds.size())).confirmUpload(any(UUID.class));
        }

        @Test
        @DisplayName("실패 - 재고 등록에 실패하면 제품이 생성되지 않는다.")
        void createProduct_StockRegistrationFails_ThrowsException() {
            // given
            given_재고_등록에_실패한다();

            // when & then
            assertThatThrownBy(() -> productService.createProduct(UUID.randomUUID(), UUID.randomUUID(), "실패 제품", 100, 10, UUID.randomUUID(), "설명", List.of()))
                    .isInstanceOf(IllegalStateException.class);

            // then
            verify(productRepository, never()).save(any(ProductEntity.class));
        }
    }

    @Nested
    @DisplayName("제품 수정 시나리오")
    class UpdateProductTest {

        @Test
        @DisplayName("성공 - 존재하는 제품 정보를 수정할 수 있다.")
        void updateProduct_Success() {
            // given
            UUID productId = UUID.randomUUID();
            given_제품이_존재한다(productId);
            List<UUID> newImageIds = List.of(UUID.randomUUID());

            // when
            productService.updateProduct(productId, UUID.randomUUID(), UUID.randomUUID(), "수정된 제품", 20000, "수정된 설명", UUID.randomUUID(), newImageIds);

            // then
            verify(productRepository, times(1)).findById(productId);
            verify(productImageRepository, times(1)).saveAll(any());
            verify(fileStorageManager, times(newImageIds.size())).confirmUpload(any(UUID.class));
            verify(productRepository, times(1)).save(any(ProductEntity.class));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 제품을 수정하면 예외가 발생한다.")
        void updateProduct_NotFound_ThrowsException() {
            // given
            UUID productId = UUID.randomUUID();
            given_제품이_존재하지_않는다(productId);

            // when & then
            assertThatThrownBy(() -> productService.updateProduct(productId, null, null, null, 0, null, UUID.randomUUID(), null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("제품 조회 시나리오")
    class GetProductTest {

        @Test
        @DisplayName("성공 - 제품, 이미지, URL이 모두 존재하면 완전한 Product 객체를 반환한다.")
        void getProduct_Success() {
            // given
            UUID productId = UUID.randomUUID();
            given_제품이_존재한다(productId);
            List<ProductImageEntity> images = given_제품의_이미지가_존재한다(productId);
            given_이미지_ObjectKey_조회에_성공한다(images.get(0).getId(), "key1.jpg");
            given_이미지_ObjectKey_조회에_성공한다(images.get(1).getId(), "key2.jpg");

            // when
            Product product = productService.getProduct(productId);

            //then
            assertThat(product.getImages())
                    .extracting(ProductImage::getImageUrl)
                    .containsExactlyInAnyOrder(
                            "https://test-domain.com/key1.jpg",
                            "https://test-domain.com/key2.jpg"
                    );

            verify(fileStorageManager, times(2)).getObjectKey(any(UUID.class));
        }

        @Test
        @DisplayName("성공 (경계값) - 제품에 이미지가 없어도 조회가 성공한다.")
        void getProduct_WithNoImages_Success() {
            // given
            UUID productId = UUID.randomUUID();
            given_제품이_존재한다(productId);
            given_제품의_이미지가_존재하지_않는다(productId);

            // when
            Product product = productService.getProduct(productId);

            // then
            assertThat(product).isNotNull();
            assertThat(product.getId()).isEqualTo(productId);
            assertThat(product.getImages()).isEmpty();
            verify(fileStorageManager, never()).getObjectKey(any());
        }

        @Test
        @DisplayName("실패 - 제품이 존재하지 않으면 예외가 발생한다.")
        void getProduct_NotFound_ThrowsException() {
            // given
            UUID productId = UUID.randomUUID();
            given_제품이_존재하지_않는다(productId);

            // when & then
            assertThatThrownBy(() -> productService.getProduct(productId))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Nested
        @DisplayName("제품 삭제 시나리오")
        class DeleteProductTest {

            @Test
            @DisplayName("성공 - 제품과 연관된 이미지를 함께 삭제한다.")
            void deleteProduct_Success() {
                // given
                UUID productId = UUID.randomUUID();
                given_제품이_존재한다(productId);
                List<ProductImageEntity> images = given_제품의_이미지가_존재한다(productId);
                List<UUID> imageIds = images.stream().map(ProductImageEntity::getId).toList();

                // when
                productService.deleteProduct(productId, UUID.randomUUID());

                // then
                verify(productRepository, times(1)).deleteById(productId);
                ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
                verify(productImageRepository, times(1)).deleteAllById(captor.capture());
                assertThat(captor.getValue()).isEqualTo(imageIds);
            }
        }

        @Nested
        @DisplayName("다중 제품 삭제 시나리오")
        class DeleteProductsTest {

            @Test
            @DisplayName("성공 - 주어진 ID 목록으로 제품들을 삭제한다.")
            void deleteProducts_Success() {
                // given
                List<UUID> productIds = List.of(UUID.randomUUID(), UUID.randomUUID());

                // when
                productService.deleteProducts(productIds, UUID.randomUUID());

                // then
                verify(productRepository, times(1)).deleteAllById(productIds);
            }
        }

        @Nested
        @DisplayName("제품 이미지 삭제 시나리오")
        class DeleteProductImageTest {

            @Test
            @DisplayName("성공 - 주어진 ID로 이미지를 삭제한다.")
            void deleteProductImage_Success() {
                // given
                UUID imageId = UUID.randomUUID();

                // when
                productService.deleteProductImage(imageId);

                // then
                verify(productImageRepository, times(1)).deleteById(imageId);
            }
        }
    }

    @Nested
    @DisplayName("카트용 제품 요약 조회 시나리오")
    class GetCartProductsTest {

        @Test
        @DisplayName("성공 - 존재하지 않는 제품 ID는 NOT_EXIST 상태로 반환한다.")
        void getCartProducts_NotExist() {
            // given
            UUID missingId = UUID.randomUUID();
            when(productRepository.findByIdIncludingDeleted(missingId)).thenReturn(Optional.ofNullable(null));

            // when
            List<ProductSummary> summaries = productService.getCartProducts(List.of(missingId));

            // then
            assertThat(summaries).hasSize(1);
            ProductSummary s = summaries.get(0);
            assertThat(s.getId()).isEqualTo(null);
            assertThat(s.getStatus().name()).isEqualTo("NOT_EXIST");
            assertThat(s.getMainImage()).isNull();
            verifyNoInteractions(stockClient);
        }

        @Test
        @DisplayName("성공 - 삭제된 제품은 NOT_EXIST 상태로 반환한다.")
        void getCartProducts_Deleted_ReturnsNotExist() {
            // given
            UUID pid = UUID.randomUUID();
            ProductEntity deleted = new ProductEntity(pid, UUID.randomUUID(), UUID.randomUUID(), "삭제된 상품", 1000, UUID.randomUUID(), "desc");

            ProductEntity spyDeleted = spy(deleted);
            when(spyDeleted.getDeletedAt()).thenReturn(LocalDateTime.now());

            when(productRepository.findByIdIncludingDeleted(pid)).thenReturn(Optional.of(spyDeleted));

            // when
            List<ProductSummary> summaries = productService.getCartProducts(List.of(pid));

            // then
            assertThat(summaries).hasSize(1);
            ProductSummary s = summaries.get(0);
            assertThat(s.getId()).isEqualTo(pid);
            assertThat(s.getStatus().name()).isEqualTo("NOT_EXIST");
            verifyNoInteractions(stockClient);
        }

        @Test
        @DisplayName("성공 - 재고 0이면 SOLD_OUT 상태로 반환한다.")
        void getCartProducts_SoldOut() {
            // given
            UUID pid = UUID.randomUUID();
            UUID mainImageId = UUID.randomUUID();
            ProductEntity entity = new ProductEntity(pid, UUID.randomUUID(), UUID.randomUUID(), "품절 상품", 2000, mainImageId, "desc");
            when(productRepository.findByIdIncludingDeleted(pid)).thenReturn(Optional.of(entity));

            ProductImageEntity img = new ProductImageEntity(mainImageId, pid);
            when(productImageRepository.findById(mainImageId)).thenReturn(Optional.of(img));
            when(fileStorageManager.getObjectKey(mainImageId)).thenReturn("img.jpg");

            StockResponseDto stock = new StockResponseDto(pid, 0, LocalDateTime.now());
            when(stockClient.getStock(pid)).thenReturn(ApiResponse.onSuccess(stock));

            // when
            List<ProductSummary> summaries = productService.getCartProducts(List.of(pid));

            // then
            assertThat(summaries).hasSize(1);
            ProductSummary s = summaries.get(0);
            assertThat(s.getStatus().name()).isEqualTo("SOLD_OUT");
            assertThat(s.getMainImage().getImageUrl()).isEqualTo("https://test-domain.com/img.jpg");
        }

        @Test
        @DisplayName("성공 - 재고가 존재하면 AVAILABLE 상태로 반환한다.")
        void getCartProducts_Available() {
            // given
            UUID pid = UUID.randomUUID();
            ProductEntity entity = new ProductEntity(pid, UUID.randomUUID(), UUID.randomUUID(), "정상 상품", 3000, null, "desc");
            when(productRepository.findByIdIncludingDeleted(pid)).thenReturn(Optional.of(entity));
//            when(productImageRepository.findAllById(any())).thenReturn(List.of());

            StockResponseDto stock = new StockResponseDto(pid, 10, LocalDateTime.now());
            when(stockClient.getStock(pid)).thenReturn(ApiResponse.onSuccess(stock));

            // when
            List<ProductSummary> summaries = productService.getCartProducts(List.of(pid));

            // then
            assertThat(summaries).hasSize(1);
            ProductSummary s = summaries.get(0);
            assertThat(s.getStatus().name()).isEqualTo("AVAILABLE");
            assertThat(s.getMainImage()).isNotNull();
        }
    }
}
