package profect.group1.goormdotcom.product;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
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
import profect.group1.goormdotcom.product.repository.RedisLockRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.service.ProductListItemCacheService;
import profect.group1.goormdotcom.product.service.utils.ImageUrlGenerator;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCircuitbrakerTest {

    @InjectMocks
    private ProductListItemCacheService service;

    @Mock private ProductRepository productRepository;
    @Mock private StockClient stockClient;
    @Mock private ImageUrlGenerator imageUrlGenerator;
    @Mock private RedisLockRepository redisLockRepository; // 사용 안함
    @Mock private org.springframework.cache.CacheManager cacheManager; // 사용 안함

    private void stubOrigin(UUID productId) {
        UUID mainImageId = UUID.randomUUID();
        ProductEntity entity = new ProductEntity(
                productId, UUID.randomUUID(), UUID.randomUUID(),
                "테스트", 100, mainImageId, "설명"
        );
        when(productRepository.findByIdIncludingDeleted(productId))
                .thenReturn(Optional.of(entity));
        when(imageUrlGenerator.generateProductImageUrl(mainImageId)).thenReturn("url");
        when(stockClient.getStock(productId)).thenReturn(
                ApiResponse.onSuccess(new StockResponseDto(productId, 10, LocalDateTime.now()))
        );
    }

    @Test
    @DisplayName("RateLimiter: 허용량 초과 시 RequestNotPermitted")
    void ratelimiter_blocks_excess_calls() {
        // given: annotate 없이 최소 의존성으로, 레이트리미터만 직접 검증
        UUID id = UUID.randomUUID();
        stubOrigin(id);

        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofSeconds(60))
                .timeoutDuration(Duration.ZERO)
                .build();
        RateLimiterRegistry registry = RateLimiterRegistry.of(config);
        RateLimiter limiter = registry.rateLimiter("productOrigin");

        Supplier<ProductListItem> supplier = () -> service.getCartProductListItemFromOrigin(id);
        Supplier<ProductListItem> limited = RateLimiter.decorateSupplier(limiter, supplier);

        // when: 2회는 통과
        limited.get();
        limited.get();

        // then: 3번째는 차단
        assertThrows(RequestNotPermitted.class, limited::get);
    }

    @Test
    @DisplayName("Bulkhead: 동시 호출 초과 시 BulkheadFullException")
    void bulkhead_blocks_excess_concurrency() throws Exception {
        // given
        UUID id = UUID.randomUUID();
        UUID main = UUID.randomUUID();
        ProductEntity e = new ProductEntity(id, UUID.randomUUID(), UUID.randomUUID(), "상품", 100, main, "desc");

        // 느린 원본 조회를 시뮬레이션하여 첫 호출이 점유 중일 때 두 번째 호출이 차단되게 함
        when(productRepository.findByIdIncludingDeleted(id)).thenAnswer(inv -> {
            Thread.sleep(150);
            return Optional.of(e);
        });
        when(imageUrlGenerator.generateProductImageUrl(main)).thenReturn("urlC");
        when(stockClient.getStock(id)).thenReturn(ApiResponse.onSuccess(new StockResponseDto(id, 5, LocalDateTime.now())));

        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ZERO)
                .build();
        Bulkhead bulkhead = Bulkhead.of("productOrigin", config);

        Supplier<ProductListItem> supplier = () -> service.getCartProductListItemFromOrigin(id);
        Supplier<ProductListItem> limited = Bulkhead.decorateSupplier(bulkhead, supplier);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        Runnable task = () -> {
            try {
                start.await();
                limited.get();
            } catch (Throwable t) {
                errors.add(t);
            }
        };

        pool.submit(task);
        pool.submit(task);
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);

        boolean hasBulkheadError = errors.stream().anyMatch(e2 -> e2 instanceof BulkheadFullException);
        assertTrue(hasBulkheadError, "BulkheadFullException 이 최소 한 번은 발생해야 함");
    }
}
