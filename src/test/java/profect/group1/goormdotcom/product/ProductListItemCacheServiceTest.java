package profect.group1.goormdotcom.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.RedisConnectionFailureException;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.repository.RedisCacheRepository;
import profect.group1.goormdotcom.product.repository.RedisLockRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductListItemEntity;
import profect.group1.goormdotcom.product.service.ProductListItemCacheService;
import profect.group1.goormdotcom.product.service.ProductListItemOriginService;

import java.time.Duration;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
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

    @Mock private ProductListItemOriginService productListItemOriginService;
    @Mock private RedisLockRepository redisLockRepository;
    @Mock private RedisCacheRepository redisCacheRepository;
    @Mock private CacheManager cacheManager;

    private ProductListItem productListItem(UUID id, String name, int price, String mainImageUrl, String status) {
        return new ProductListItem(id, name, price, mainImageUrl, status);
    }

    @BeforeEach
    void resetMocks() {
        clearInvocations(productListItemOriginService, redisLockRepository, redisCacheRepository, cacheManager);
    }

    @Nested
    @DisplayName("단건 조회")
    class SingleGet {
        @Test
        @DisplayName("db fallback: 캐시 연결 실패 시 원본으로 폴백")
        void dbFallback_onCacheConnectionFailure() {
            // given
            UUID id = UUID.randomUUID();
            ProductListItem origin = productListItem(id, "A", 1000, "urlA", "AVAILABLE");

            Cache mockCache = mock(Cache.class);
            when(cacheManager.getCache("product-list-item:cart")).thenReturn(mockCache);
            when(mockCache.get(eq(id), eq(ProductListItem.class)))
                    .thenThrow(new RedisConnectionFailureException("cache down"));
            when(productListItemOriginService.getCartProductListItemFromOrigin(id)).thenReturn(origin);

            // when
            ProductListItem item = service.getCartProductListItem(id);

            // then
            assertThat(item.getId()).isEqualTo(id);
            assertThat(item.getMainImageUrl()).isEqualTo("urlA");
            verify(productListItemOriginService, times(1)).getCartProductListItemFromOrigin(id);
            verify(mockCache, never()).put(any(), any());
        }

        @Test
        @DisplayName("캐시 미스 put: 첫 호출 후 캐시에 저장")
        void cachePutOnMiss() {
            // given
            UUID id = UUID.randomUUID();
            ProductListItem origin = productListItem(id, "B", 2000, "urlB", "AVAILABLE");

            ConcurrentMapCache cache = new ConcurrentMapCache("product-list-item:cart");
            when(cacheManager.getCache("product-list-item:cart")).thenReturn(cache);

            when(redisLockRepository.lock(anyString())).thenReturn(true);
            when(redisLockRepository.unlock(anyString())).thenReturn(true);
            when(productListItemOriginService.getCartProductListItemFromOrigin(id)).thenReturn(origin);

            // when
            ProductListItem first = service.getCartProductListItem(id);

            // then
            assertThat(first.getId()).isEqualTo(id);
            ProductListItem cached = cache.get(id, ProductListItem.class);
            assertThat(cached).isNotNull();
            assertThat(cached.getMainImageUrl()).isEqualTo("urlB");
            verify(productListItemOriginService, times(1)).getCartProductListItemFromOrigin(id);
        }

        @Test
        @DisplayName("캐시 갱신 락: 동시 다중 호출에도 원본 호출은 1회")
        void cacheRefreshLock_singleLoaderCall() throws Exception {
            // given
            UUID id = UUID.randomUUID();
            ProductListItem origin = productListItem(id, "C", 3000, "urlC", "AVAILABLE");

            ConcurrentMapCache cache = new ConcurrentMapCache("product-list-item:cart");
            when(cacheManager.getCache("product-list-item:cart")).thenReturn(cache);

            // 첫 호출만 lock=true, 이후는 lock=false 로 시뮬레이션
            AtomicBoolean first = new AtomicBoolean(true);
            when(redisLockRepository.lock(anyString())).thenAnswer(inv -> first.getAndSet(false));
            when(redisLockRepository.unlock(anyString())).thenReturn(true);
            when(productListItemOriginService.getCartProductListItemFromOrigin(id)).thenReturn(origin);

            int threads = 8;
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
            verify(productListItemOriginService, times(1)).getCartProductListItemFromOrigin(id);
            ProductListItem cached = cache.get(id, ProductListItem.class);
            assertThat(cached).isNotNull();
        }
    }

    @Nested
    @DisplayName("다건 조회")
    class BulkGet {
        @Test
        @DisplayName("모두 캐시 히트: 순서 유지, 원본/저장 미호출")
        void bulk_allCacheHit_inOrder_noOriginNoPut() {
            // given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<UUID> ids = List.of(id1, id2);

            List<ProductListItemEntity> cached = List.of(
                    new ProductListItemEntity(id1, "A", 100, "u1", "AVAILABLE"),
                    new ProductListItemEntity(id2, "B", 200, "u2", "SOLD_OUT")
            );

            when(redisCacheRepository.getCartProductListItemsBulk(ids)).thenReturn(cached);

            // when
            List<ProductListItem> result = service.getCartProductListItemsBulk(ids);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo(id1);
            assertThat(result.get(1).getId()).isEqualTo(id2);
            verify(productListItemOriginService, never()).getCartProductListItemsBulkFromOrigin(anyList());
            verify(redisCacheRepository, never()).putCartProductListItemsBulk(anyList(), any());
        }

        @Test
        @DisplayName("부분 캐시 미스: miss ID만 원본 조회+캐시 저장, 순서 유지")
        void bulk_partialMiss_fetchMissing_andCache() {
            // given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            List<UUID> ids = List.of(id1, id2, id3);

            // 캐시는 id1만 히트
            when(redisCacheRepository.getCartProductListItemsBulk(ids))
                    .thenReturn(List.of(new ProductListItemEntity(id1, "A", 100, "u1", "AVAILABLE")));

            // 원본에서 id2, id3만 조회
            List<ProductListItem> originItems = List.of(
                    productListItem(id2, "B", 200, "u2", "SOLD_OUT"),
                    productListItem(id3, "C", 300, "u3", "AVAILABLE")
            );
            when(productListItemOriginService.getCartProductListItemsBulkFromOrigin(List.of(id2, id3)))
                    .thenReturn(originItems);

            // when
            List<ProductListItem> result = service.getCartProductListItemsBulk(ids);

            // then: 순서 유지 [id1, id2, id3]
            assertThat(result).extracting(ProductListItem::getId)
                    .containsExactly(id1, id2, id3);

            // miss ID만 원본 호출
            verify(productListItemOriginService, times(1))
                    .getCartProductListItemsBulkFromOrigin(eq(List.of(id2, id3)));

            // 캐시 저장: 엔터티 목록과 TTL 60초
            verify(redisCacheRepository, times(1))
                    .putCartProductListItemsBulk(argThat(list -> list.size() == 2
                            && list.stream().map(ProductListItemEntity::getId)
                                    .sorted().toList()
                                    .equals(List.of(id2, id3).stream().sorted().toList())
                    ), eq(Duration.ofSeconds(60)));
        }
    }
}
