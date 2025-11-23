package profect.group1.goormdotcom.product.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import profect.group1.goormdotcom.product.domain.Product;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.repository.RedisCacheRepository;
import profect.group1.goormdotcom.product.repository.RedisLockRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductListItemEntity;
import profect.group1.goormdotcom.product.repository.mapper.ProductListItemMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductListItemCacheService {
    private final ProductListItemOriginService productListItemOriginService;
    private final RedisLockRepository redisLockRepository;
    private final RedisCacheRepository redisCacheRepository;
    private final CacheManager cacheManager;

    private final String LOCK_PREFIX = "lock:product-list-item:";

    // 단건 조회 로직
    public ProductListItem getCartProductListItem(UUID productId) { // 캐시 조회 로직
            Cache cache = cacheManager.getCache("product-list-item:cart");

            if (cache == null) {
                log.info("Cache not found");
                return productListItemOriginService.getCartProductListItemFromOrigin(productId);
            }

            ProductListItem cachedItem;
            try {
                cachedItem = cache.get(productId, ProductListItem.class);
            } catch (RedisConnectionFailureException e) {
                log.error("Cache connection failure : DB fallback");
                return productListItemOriginService.getCartProductListItemFromOrigin(productId);
            }

            if (cachedItem != null) {
                log.info("Product list item {} cache hit", productId);
                return cachedItem;
            }

            String lockKey = "lock:product-list-item:cart:" + productId;
            String token = "";
            try {
                token = redisLockRepository.tryLock(lockKey, Duration.ofMillis(3000));
                if (token == null) {
                    try {
                        log.info("Failed to get lock key... waiting for 1s");
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }

                    // 더블 체크
                    cachedItem = cache.get(productId, ProductListItem.class);
                    if (cachedItem != null) {
                        log.info("Product list item {} cache hit in double check", productId);
                        return cachedItem;
                    }
                    log.info("Product list item {} cache miss in double check", productId);
                }

                log.info("Product list item {} get lock", productId);
                ProductListItem productListItem = productListItemOriginService.getCartProductListItemFromOrigin(productId);
                cache.put(productId, productListItem);
                return productListItem;
            } finally {
                redisLockRepository.unlock(lockKey, token);
            }
    }

    // 다건 조회 로직
    public List<ProductListItem> getCartProductListItemsBulk(List<UUID> productIds) { // 캐시 조회 로직
        // 캐시 조회
        List<ProductListItemEntity> cachedEntities = redisCacheRepository.getCartProductListItemsBulk(productIds);
        log.info("Product list items {} cache hit", cachedEntities.size());

        // 캐시 map 생성
        Map<UUID, ProductListItem> cachedMap = cachedEntities.stream()
                .map(ProductListItemMapper::toDomain)
                .collect(Collectors.toMap(
                        ProductListItem::getId,
                        item -> item
                ));

        // missing productId
        List<UUID> missIds = productIds.stream().filter(id -> !cachedMap.containsKey(id)).toList();

        // Lock
        List<String> lockKeys = missIds.stream().map(id -> LOCK_PREFIX + id).toList();
        log.info("Product list items {} cache miss", missIds.size());
        if (!missIds.isEmpty()) {
            Map<String, UUID> lockKeysMap = missIds.stream()
                    .collect(Collectors.toMap(id -> LOCK_PREFIX + id, id -> id));
            Map<String, String> lockTokenMap = redisLockRepository.tryLocksBulk(lockKeys, Duration.ofMillis(3000));

            // 락 획득한 id
            List<UUID> lockIds = lockTokenMap.keySet().stream().map(lockKeysMap::get).toList();
            log.info("Product list items {} get lock", lockIds.size());
            // 락 획득하지 못한 id
            List<UUID> noLockIds = lockKeysMap.keySet().stream().filter(lockKey -> !lockTokenMap.containsKey(lockKey)).map(lockKeysMap::get).toList();

            if (!lockIds.isEmpty()) {
                // DB 조회
                List<ProductListItem> productListItemsFromOrigin = productListItemOriginService.getCartProductListItemsBulkFromOrigin(lockIds);
                // 엔터티로 변경
                List<ProductListItemEntity> entitiesToCache = productListItemsFromOrigin.stream().map(ProductListItemMapper::toEntity).toList();
                // 캐시 저장
                // TODO: jitter 추가
                redisCacheRepository.putCartProductListItemsBulk(entitiesToCache, Duration.ofSeconds(60));
                log.info("Put missed Product Items {} to Cache", entitiesToCache.size());
                productListItemsFromOrigin.forEach(item -> cachedMap.put(item.getId(), item));
                redisLockRepository.unLocksBulk(lockTokenMap);
            }

            if (!noLockIds.isEmpty()) {
                try {
                    // 백오프
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }

                // 더블 체크
                List<ProductListItem> doubleCheckedCachedItemList = redisCacheRepository.getCartProductListItemsBulk(noLockIds)
                        .stream().map(ProductListItemMapper::toDomain).toList();
                if (!doubleCheckedCachedItemList.isEmpty()) {
                    doubleCheckedCachedItemList.forEach(item -> cachedMap.put(item.getId(), item));
                    log.info("Product list items {} cache hit in double check", doubleCheckedCachedItemList.size());
                } else {
                    // 최종적으로 DB 조회
                    List<ProductListItem> doubleCheckedItemsFromOriginList = productListItemOriginService.getCartProductListItemsBulkFromOrigin(lockIds);
                    List<ProductListItemEntity> doubleCheckedEntitiesToCache = doubleCheckedItemsFromOriginList.stream().map(ProductListItemMapper::toEntity).toList();
                    redisCacheRepository.putCartProductListItemsBulk(doubleCheckedEntitiesToCache, Duration.ofSeconds(60));
                    doubleCheckedItemsFromOriginList.forEach(item -> cachedMap.put(item.getId(), item));
                    log.info("Product list items {} from origin in double check", doubleCheckedItemsFromOriginList.size());
                }

            }
        }

        return productIds.stream()
                .map(cachedMap::get)
                .toList();
    }
}
