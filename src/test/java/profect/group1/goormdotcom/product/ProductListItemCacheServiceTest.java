package profect.group1.goormdotcom.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.RedisConnectionFailureException;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.StockClient;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.dto.StockResponseDto;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.RedisLockRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.service.ProductListItemCacheService;
import profect.group1.goormdotcom.product.service.utils.ImageUrlGenerator;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductListItemCacheService 캐시/락 테스트")
class ProductListItemCacheServiceTest {

    @InjectMocks
    private ProductListItemCacheService service;

    @Mock private ProductRepository productRepository;
    @Mock private StockClient stockClient;
    @Mock private ImageUrlGenerator imageUrlGenerator;
    @Mock private RedisLockRepository redisLockRepository;
    @Mock private CacheManager cacheManager;

    private ProductEntity entity(UUID id, String name, int price, UUID mainImageId) {
        return new ProductEntity(id, UUID.randomUUID(), UUID.randomUUID(), name, price, mainImageId, "desc");
    }

    @BeforeEach
    void resetMocks() {
        clearInvocations(productRepository, stockClient, imageUrlGenerator, redisLockRepository, cacheManager);
    }

    @Test
    @DisplayName("db fallback: 캐시 연결 실패 시 원본으로 폴백")
    void dbFallback_onCacheConnectionFailure() {
        // given
        UUID id = UUID.randomUUID();
        UUID main = UUID.randomUUID();
        ProductEntity e = entity(id, "A", 1000, main);

        Cache mockCache = mock(Cache.class);
        when(cacheManager.getCache("product-list-item:cart")).thenReturn(mockCache);
        when(mockCache.get(eq(id), eq(ProductListItem.class)))
                .thenThrow(new RedisConnectionFailureException("cache down"));

        when(productRepository.findByIdIncludingDeleted(id)).thenReturn(Optional.of(e));
        when(imageUrlGenerator.generateProductImageUrl(main)).thenReturn("urlA");
        when(stockClient.getStock(id)).thenReturn(ApiResponse.onSuccess(new StockResponseDto(id, 3, LocalDateTime.now())));

        // when
        ProductListItem item = service.getCartProductListItem(id);

        // then
        assertThat(item.getId()).isEqualTo(id);
        assertThat(item.getMainImageUrl()).isEqualTo("urlA");
        verify(productRepository, times(1)).findByIdIncludingDeleted(id);
        verify(stockClient, times(1)).getStock(id);
        verify(imageUrlGenerator, times(1)).generateProductImageUrl(main);
        verify(mockCache, never()).put(any(), any());
    }

    @Test
    @DisplayName("캐시 미스 put: 첫 호출 후 캐시에 저장")
    void cachePutOnMiss() {
        // given
        UUID id = UUID.randomUUID();
        UUID main = UUID.randomUUID();
        ProductEntity e = entity(id, "B", 2000, main);

        ConcurrentMapCache cache = new ConcurrentMapCache("product-list-item:cart");
        when(cacheManager.getCache("product-list-item:cart")).thenReturn(cache);

        when(redisLockRepository.lock(anyString())).thenReturn(true);
        when(redisLockRepository.unlock(anyString())).thenReturn(true);

        when(productRepository.findByIdIncludingDeleted(id)).thenReturn(Optional.of(e));
        when(imageUrlGenerator.generateProductImageUrl(main)).thenReturn("urlB");
        when(stockClient.getStock(id)).thenReturn(ApiResponse.onSuccess(new StockResponseDto(id, 5, LocalDateTime.now())));

        // when
        ProductListItem first = service.getCartProductListItem(id);

        // then
        assertThat(first.getId()).isEqualTo(id);
        ProductListItem cached = cache.get(id, ProductListItem.class);
        assertThat(cached).isNotNull();
        assertThat(cached.getMainImageUrl()).isEqualTo("urlB");
    }

    @Test
    @DisplayName("캐시 갱신 락: 동시 다중 호출에도 로더 1회만 실행")
    void cacheRefreshLock_singleLoaderCall() throws Exception {
        // given
        UUID id = UUID.randomUUID();
        UUID main = UUID.randomUUID();
        ProductEntity e = entity(id, "C", 3000, main);

        ConcurrentMapCache cache = new ConcurrentMapCache("product-list-item:cart");
        when(cacheManager.getCache("product-list-item:cart")).thenReturn(cache);

        // 첫 호출만 lock=true, 이후는 lock=false 로 시뮬레이션
        AtomicBoolean first = new AtomicBoolean(true);
        when(redisLockRepository.lock(anyString())).thenAnswer(inv -> first.getAndSet(false));
        when(redisLockRepository.unlock(anyString())).thenReturn(true);

        when(productRepository.findByIdIncludingDeleted(id)).thenReturn(Optional.of(e));
        when(imageUrlGenerator.generateProductImageUrl(main)).thenReturn("urlC");
        when(stockClient.getStock(id)).thenReturn(ApiResponse.onSuccess(new StockResponseDto(id, 7, LocalDateTime.now())));

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    service.getCartProductListItem(id);
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(2, TimeUnit.SECONDS);
        pool.shutdown();

        // then: 락 획득 스레드만 로더 실행, 나머지는 더블체크에서 캐시 히트
        verify(productRepository, times(1)).findByIdIncludingDeleted(id);
        verify(stockClient, times(1)).getStock(id);
        verify(imageUrlGenerator, times(1)).generateProductImageUrl(main);

        ProductListItem cached = cache.get(id, ProductListItem.class);
        assertThat(cached).isNotNull();
    }
}

