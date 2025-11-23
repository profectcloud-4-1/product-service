package profect.group1.goormdotcom.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
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
import java.util.*;
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

            when(redisLockRepository.tryLock(anyString(), any(Duration.class))).thenReturn("tkn");
            doNothing().when(redisLockRepository).unlock(anyString(), anyString());
            when(productListItemOriginService.getCartProductListItemFromOrigin(id)).thenReturn(origin);

            // when
            ProductListItem first = service.getCartProductListItem(id);

            // then
            assertThat(first.getId()).isEqualTo(id);
            ProductListItem cached = cache.get(id, ProductListItem.class);
            assertThat(cached).isNotNull();
            assertThat(cached.getMainImageUrl()).isEqualTo("urlB");
            verify(productListItemOriginService, times(1)).getCartProductListItemFromOrigin(id);
            // 락 획득/해제 호출 검증
            verify(redisLockRepository, atLeastOnce()).tryLock(startsWith("lock:product-list-item:cart:"), any(Duration.class));
            verify(redisLockRepository).unlock(eq("lock:product-list-item:cart:" + id), eq("tkn"));
        }

        @Test
        @DisplayName("더블체크: 락 실패 후 캐시 재조회에서 히트되면 원본 미호출")
        void doubleCheck_onLockFail_returnsCachedWithoutOrigin() {
            // given
            UUID id = UUID.randomUUID();
            ProductListItem cached = productListItem(id, "D", 1500, "urlD", "AVAILABLE");

            Cache mockCache = mock(Cache.class);
            when(cacheManager.getCache("product-list-item:cart")).thenReturn(mockCache);

            // 첫 번째 get은 미스(null), 더블체크 시에는 히트(cached)
            when(mockCache.get(eq(id), eq(ProductListItem.class))).thenReturn(null, cached);

            // 락 획득 실패 시나리오
            when(redisLockRepository.tryLock(anyString(), any(Duration.class))).thenReturn(null);
            doNothing().when(redisLockRepository).unlock(anyString(), isNull());

            // when
            ProductListItem result = service.getCartProductListItem(id);

            // then: 더블체크에서 캐시 히트, 원본 호출 없음
            assertThat(result).isEqualTo(cached);
            verify(productListItemOriginService, never()).getCartProductListItemFromOrigin(any());
            // put 호출도 없음 (더블체크로 반환)
            verify(mockCache, never()).put(any(), any());
            // 락 시도 후 실패 -> unlock은 빈 토큰으로 호출됨
            verify(redisLockRepository, atLeastOnce()).tryLock(eq("lock:product-list-item:cart:" + id), any(Duration.class));
            verify(redisLockRepository).unlock(eq("lock:product-list-item:cart:" + id), isNull());
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
            when(redisLockRepository.tryLock(anyString(), any(Duration.class)))
                    .thenAnswer(inv -> first.getAndSet(false) ? "tkn" : null);
            doNothing().when(redisLockRepository).unlock(anyString(), anyString());
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
            // 락 호출 1회, 해제 1회(token=tkn)
            verify(redisLockRepository, atLeastOnce()).tryLock(eq("lock:product-list-item:cart:" + id), any(Duration.class));
            verify(redisLockRepository).unlock(eq("lock:product-list-item:cart:" + id), eq("tkn"));
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

            // 캐시는 id1만 히트 (정확한 인자 매칭)
            when(redisCacheRepository.getCartProductListItemsBulk(eq(ids)))
                    .thenReturn(List.of(new ProductListItemEntity(id1, "A", 100, "u1", "AVAILABLE")));

            // 원본에서 id2, id3만 조회
            List<ProductListItem> originItems = List.of(
                    productListItem(id2, "B", 200, "u2", "SOLD_OUT"),
                    productListItem(id3, "C", 300, "u3", "AVAILABLE")
            );
            when(productListItemOriginService.getCartProductListItemsBulkFromOrigin(
                    argThat(l -> l != null && l.size() == 2 && l.containsAll(List.of(id2, id3)))
            )).thenReturn(originItems);

            // 락: missIds 모두 락 획득하도록 스텁하여 double-check 분기 없이 원본 호출 경로만 타게 함
            when(redisLockRepository.tryLocksBulk(anyList(), any(Duration.class)))
                    .thenAnswer(inv -> {
                        List<String> keys = inv.getArgument(0);
                        Map<String, String> tokenMap = new HashMap<>();
                        for (String k : keys) tokenMap.put(k, "tkn");
                        return tokenMap;
                    });
            // unlocks bulk는 void
            doNothing().when(redisLockRepository).unLocksBulk(anyMap());

            // when
            List<ProductListItem> result = service.getCartProductListItemsBulk(ids);

            // then: 순서 유지 [id1, id2, id3]
            assertThat(result).extracting(ProductListItem::getId)
                    .containsExactly(id1, id2, id3);

            // miss ID만 원본 호출
            // 호출 인자 검증 (순서 무관)
            ArgumentCaptor<List<UUID>> missCaptor = ArgumentCaptor.forClass(List.class);
            verify(productListItemOriginService, times(1))
                    .getCartProductListItemsBulkFromOrigin(missCaptor.capture());
            List<UUID> calledMiss = missCaptor.getValue();
            assertThat(new java.util.HashSet<>(calledMiss))
                    .isEqualTo(new java.util.HashSet<>(List.of(id2, id3)));

            // 캐시 저장: 엔터티 목록과 TTL 60초
            verify(redisCacheRepository, times(1))
                    .putCartProductListItemsBulk(argThat(list -> list.size() == 2
                            && list.stream().map(ProductListItemEntity::getId)
                                    .sorted().toList()
                                    .equals(List.of(id2, id3).stream().sorted().toList())
                    ), eq(Duration.ofSeconds(60)));

            // 락 획득/해제 호출 검증
            verify(redisLockRepository, times(1)).tryLocksBulk(anyList(), any(Duration.class));
            verify(redisLockRepository, times(1)).unLocksBulk(anyMap());
        }

        @Test
        @DisplayName("더블체크 분기: 락 모두 실패(noLockIds=missIds) 후 더블체크 캐시 히트")
        void bulk_doubleCheck_cacheHit_forNoLockIds() {
            // given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            List<UUID> ids = List.of(id1, id2, id3);

            // 처음 캐시: id1만 히트
            when(redisCacheRepository.getCartProductListItemsBulk(eq(ids)))
                    .thenReturn(List.of(new ProductListItemEntity(id1, "A", 100, "u1", "AVAILABLE")));

            // 락 전부 실패하도록(빈 토큰 맵)
            when(redisLockRepository.tryLocksBulk(anyList(), any(Duration.class)))
                    .thenReturn(new java.util.HashMap<>());

            // 더블체크에서 noLockIds = [id2, id3]를 캐시 히트로 돌려줌
            when(redisCacheRepository.getCartProductListItemsBulk(
                    argThat(list -> list != null && list.size() == 2 && list.containsAll(List.of(id2, id3)))
            )).thenReturn(List.of(
                    new ProductListItemEntity(id2, "B", 200, "u2", "SOLD_OUT"),
                    new ProductListItemEntity(id3, "C", 300, "u3", "AVAILABLE")
            ));

            // when
            List<ProductListItem> result = service.getCartProductListItemsBulk(ids);

            // then: 모두 채워지고 원본 호출 없음
            assertThat(result).extracting(ProductListItem::getId)
                    .containsExactly(id1, id2, id3);
            verify(productListItemOriginService, never()).getCartProductListItemsBulkFromOrigin(anyList());
            // 캐시 저장도 없음(락 전부 실패라 origin 경로 안탐)
            verify(redisCacheRepository, never()).putCartProductListItemsBulk(anyList(), any());
            // 언락도 호출 안됨
            verify(redisLockRepository, never()).unLocksBulk(anyMap());
        }

        @Test
        @DisplayName("더블체크 분기: 일부만 락 성공(lockIds=[id2]), 더블체크 미스 → origin이 lockIds로 다시 호출")
        void bulk_doubleCheck_miss_callsOriginWithLockIdsTwice_andLeavesNoLockMissing() {
            // given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            List<UUID> ids = List.of(id1, id2, id3);

            // 처음 캐시: id1만 히트
            when(redisCacheRepository.getCartProductListItemsBulk(eq(ids)))
                    .thenReturn(List.of(new ProductListItemEntity(id1, "A", 100, "u1", "AVAILABLE")));

            // 락 키 생성 로직에 따라 id2만 락 성공시키는 맵 구성
            when(redisLockRepository.tryLocksBulk(anyList(), any(Duration.class)))
                    .thenAnswer(inv -> {
                        List<String> keys = inv.getArgument(0);
                        java.util.Map<String, String> tokens = new java.util.HashMap<>();
                        for (String k : keys) {
                            if (k.endsWith(id2.toString())) tokens.put(k, "tkn2");
                        }
                        return tokens;
                    });

            // lockIds=[id2] 원본 조회 결과
            List<ProductListItem> originForId2 = List.of(
                    productListItem(id2, "B", 200, "u2", "SOLD_OUT")
            );
            when(productListItemOriginService.getCartProductListItemsBulkFromOrigin(
                    argThat(list -> list != null && list.size() == 1 && list.contains(id2))
            )).thenReturn(originForId2);

            // 더블체크: noLockIds=[id3]로 들어오면 캐시 미스 반환
            when(redisCacheRepository.getCartProductListItemsBulk(
                    argThat(list -> list != null && list.size() == 1 && list.contains(id3))
            )).thenReturn(List.of());

            // when
            List<ProductListItem> result = service.getCartProductListItemsBulk(ids);

            // then
            // origin이 lockIds(id2)로 2번 호출됨(한 번은 락 획득 분기, 한 번은 더블체크 미스 분기)
            verify(productListItemOriginService, atLeast(2))
                    .getCartProductListItemsBulkFromOrigin(argThat(list -> list.size() == 1 && list.contains(id2)));

            // id1, id2는 값이 있고 id3은 여전히 비어있음(현재 구현 기준)
            assertThat(result.get(0).getId()).isEqualTo(id1);
            assertThat(result.get(1).getId()).isEqualTo(id2);
            assertThat(result.get(2)).isNull();

            // 캐시 저장은 id2에 대해서만 2회 수행됨(락 획득 분기 1회 + 더블체크 미스 분기 1회)
            verify(redisCacheRepository, times(2))
                    .putCartProductListItemsBulk(argThat(list -> list.size() == 1
                            && list.get(0).getId().equals(id2)
                    ), eq(Duration.ofSeconds(60)));

            // 언락 호출은 1회
            verify(redisLockRepository, times(1)).unLocksBulk(anyMap());
        }
    }
}
