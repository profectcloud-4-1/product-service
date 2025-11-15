package profect.group1.goormdotcom.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.file.FileStorageManager;
import profect.group1.goormdotcom.product.domain.ProductSummary;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.StockClient;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.dto.StockResponseDto;
import profect.group1.goormdotcom.product.repository.ProductImageRepository;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.ProductSummaryRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.repository.entity.ProductImageEntity;
import profect.group1.goormdotcom.product.repository.entity.ProductSummaryEntity;
import profect.group1.goormdotcom.product.service.ProductSummaryService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductSummaryService 비즈니스 로직 테스트")
public class ProductSummaryServiceTest {

    @InjectMocks
    private ProductSummaryService productSummaryService;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private ProductSummaryRepository productSummaryRepository;
    @Mock
    private StockClient stockClient;
    @Mock
    private FileStorageManager fileStorageManager;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(productSummaryService, "cloudfrontDomain", "https://test-domain.com/");
        ReflectionTestUtils.setField(productSummaryService, "defaultImageObjectKey", "img.jpg");
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
            List<ProductSummary> summaries = productSummaryService.getCartProductsV1(List.of(missingId));

            // then
            assertThat(summaries).hasSize(1);
            ProductSummary s = summaries.get(0);
            assertThat(s.getId()).isEqualTo(missingId);
            assertThat(s.getStatus()).isEqualTo("NOT_EXIST");
            assertThat(s.getMainImageUrl()).isEqualTo("https://test-domain.com/img.jpg");
            verifyNoInteractions(stockClient);
        }

        @Test
        @DisplayName("성공 - 삭제된 제품은 NOT_EXIST 상태로 반환한다.")
        void getCartProducts_Deleted_ReturnsNotExist() {
            // given
            UUID pid = UUID.randomUUID();
            ProductEntity deleted = new ProductEntity(pid, UUID.randomUUID(), UUID.randomUUID(), "삭제된 상품", 1000, UUID.randomUUID(), "desc", "img.jpg");

            ProductEntity spyDeleted = spy(deleted);
            when(spyDeleted.getDeletedAt()).thenReturn(LocalDateTime.now());

            when(productRepository.findByIdIncludingDeleted(pid)).thenReturn(Optional.of(spyDeleted));

            // when
            List<ProductSummary> summaries = productSummaryService.getCartProductsV1(List.of(pid));

            // then
            assertThat(summaries).hasSize(1);
            ProductSummary s = summaries.get(0);
            assertThat(s.getId()).isEqualTo(pid);
            assertThat(s.getStatus()).isEqualTo("NOT_EXIST");
            verifyNoInteractions(stockClient);
        }

        @Test
        @DisplayName("성공 - 재고 0이면 SOLD_OUT 상태로 반환한다.")
        void getCartProducts_SoldOut() {
            // given
            UUID pid = UUID.randomUUID();
            UUID mainImageId = UUID.randomUUID();
            ProductEntity entity = new ProductEntity(pid, UUID.randomUUID(), UUID.randomUUID(), "품절 상품", 2000, mainImageId, "desc", "img.jpg");
            when(productRepository.findByIdIncludingDeleted(pid)).thenReturn(Optional.of(entity));

            ProductImageEntity img = new ProductImageEntity(mainImageId, pid);
            when(productImageRepository.findById(mainImageId)).thenReturn(Optional.of(img));
            when(fileStorageManager.getObjectKey(mainImageId)).thenReturn("img.jpg");

            StockResponseDto stock = new StockResponseDto(pid, 0, LocalDateTime.now());
            when(stockClient.getStock(pid)).thenReturn(ApiResponse.onSuccess(stock));

            // when
            List<ProductSummary> summaries = productSummaryService.getCartProductsV1(List.of(pid));

            // then
            assertThat(summaries).hasSize(1);
            ProductSummary s = summaries.get(0);
            assertThat(s.getStatus()).isEqualTo("SOLD_OUT");
            assertThat(s.getMainImageUrl()).isEqualTo("https://test-domain.com/img.jpg");
        }

        @Test
        @DisplayName("성공 - 재고가 존재하면 AVAILABLE 상태로 반환한다.")
        void getCartProducts_Available() {
            // given
            UUID pid = UUID.randomUUID();
            ProductEntity entity = new ProductEntity(pid, UUID.randomUUID(), UUID.randomUUID(), "정상 상품", 3000, null, "desc", "img.jpg");
            when(productRepository.findByIdIncludingDeleted(pid)).thenReturn(Optional.of(entity));

            StockResponseDto stock = new StockResponseDto(pid, 10, LocalDateTime.now());
            when(stockClient.getStock(pid)).thenReturn(ApiResponse.onSuccess(stock));

            // when
            List<ProductSummary> summaries = productSummaryService.getCartProductsV1(List.of(pid));

            // then
            assertThat(summaries).hasSize(1);
            ProductSummary s = summaries.get(0);
            assertThat(s.getStatus()).isEqualTo("AVAILABLE");
            assertThat(s.getMainImageUrl()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getCartProducts 캐싱 동작 테스트")
    class GetCartProductsCacheTest {

        @Test
        @DisplayName("성공 - 전부 캐시에 존재하면 저장 없이 그대로 반환")
        void getCartProducts_AllCached() {
            // given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            var cached1 = profect.group1.goormdotcom.product.repository.entity.ProductSummaryEntity.builder()
                    .id(id1).name("A").price(1000).mainImageUrl("u1").status("AVAILABLE").build();
            var cached2 = profect.group1.goormdotcom.product.repository.entity.ProductSummaryEntity.builder()
                    .id(id2).name("B").price(2000).mainImageUrl("u2").status("SOLD_OUT").build();

            when(productSummaryRepository.findAllById(List.of(id1, id2)))
                    .thenReturn(List.of(cached1, cached2));

            // when
            List<ProductSummary> result = productSummaryService.getCartProducts(List.of(id1, id2));

            // then
            assertThat(result).hasSize(2);
            assertThat(result.stream().map(ProductSummary::getId).toList()).containsExactlyInAnyOrder(id1, id2);
            // 미스가 없으므로 저장은 빈 목록으로 호출됨
            verify(productSummaryRepository).saveAll(argThat((Iterable<ProductSummaryEntity> it) -> !it.iterator().hasNext()));
            // 하위 저장소들과의 상호작용 없음
            verifyNoInteractions(productRepository, productImageRepository, stockClient, fileStorageManager);
        }

        @Test
        @DisplayName("성공 - 일부만 캐시에 있으면 미스만 조회/저장")
        void getCartProducts_PartialCached() {
            // given
            UUID cachedId = UUID.randomUUID();
            UUID missId = UUID.randomUUID();

            var cached = profect.group1.goormdotcom.product.repository.entity.ProductSummaryEntity.builder()
                    .id(cachedId).name("C").price(1500).mainImageUrl("uc").status("AVAILABLE").build();

            when(productSummaryRepository.findAllById(List.of(cachedId, missId)))
                    .thenReturn(List.of(cached));

            // 미스 ID에 대한 v1 내부 흐름 의존성 설정
            ProductEntity entity = new ProductEntity(missId, UUID.randomUUID(), UUID.randomUUID(), "미스상품", 5000, null, "desc", "img.jpg");
            when(productRepository.findByIdIncludingDeleted(missId)).thenReturn(Optional.of(entity));
            when(stockClient.getStock(missId)).thenReturn(ApiResponse.onSuccess(new StockResponseDto(missId, 3, LocalDateTime.now())));

            // when
            List<ProductSummary> result = productSummaryService.getCartProducts(List.of(cachedId, missId));

            // then
            assertThat(result).hasSize(2);
            // 반환 항목에 캐시된 것과 미스 처리된 것이 모두 포함됨
            assertThat(result.stream().map(ProductSummary::getId).toList()).containsExactlyInAnyOrder(cachedId, missId);

            // 미스 1건만 저장되었는지 확인
            verify(productSummaryRepository).saveAll(argThat((Iterable<ProductSummaryEntity> it) -> {
                int count = 0;
                UUID only = null;
                for (ProductSummaryEntity e : it) {
                    count++;
                    only = e.getId();
                }
                return count == 1 && missId.equals(only);
            }));
        }

        @Test
        @DisplayName("성공 - 캐시에 없으면 모두 조회/저장")
        void getCartProducts_NoneCached() {
            // given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            when(productSummaryRepository.findAllById(List.of(id1, id2)))
                    .thenReturn(List.of());

            ProductEntity e1 = new ProductEntity(id1, UUID.randomUUID(), UUID.randomUUID(), "p1", 1000, null, "d", "i");
            ProductEntity e2 = new ProductEntity(id2, UUID.randomUUID(), UUID.randomUUID(), "p2", 2000, null, "d", "i");
            when(productRepository.findByIdIncludingDeleted(id1)).thenReturn(Optional.of(e1));
            when(productRepository.findByIdIncludingDeleted(id2)).thenReturn(Optional.of(e2));
            when(stockClient.getStock(any(UUID.class))).thenReturn(ApiResponse.onSuccess(new StockResponseDto(id1, 1, LocalDateTime.now())));

            // when
            List<ProductSummary> result = productSummaryService.getCartProducts(List.of(id1, id2));

            // then
            assertThat(result).hasSize(2);
            assertThat(result.stream().map(ProductSummary::getId).toList()).containsExactlyInAnyOrder(id1, id2);
            verify(productSummaryRepository).saveAll(argThat((Iterable<ProductSummaryEntity> it) -> {
                java.util.List<UUID> ids = new java.util.ArrayList<>();
                for (ProductSummaryEntity e : it) ids.add(e.getId());
                return ids.size() == 2 && ids.containsAll(List.of(id1, id2));
            }));
        }
    }
}
