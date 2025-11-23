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

            try {
                Boolean locked = redisLockRepository.lock(lockKey);
                if (!locked) {
                    try {
                        log.info("Failed to get lock key... waiting for 1s", productId);
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
                Boolean result = redisLockRepository.unlock(lockKey);
                if (result) {
                    log.info("Product list item {} cache unlock", productId);
                }
            }
    }

    // 다건 조회 로직
    public List<ProductListItem> getCartProductListItemsBulk(List<UUID> productIds) { // 캐시 조회 로직
        // 캐시 조회
        List<ProductListItemEntity> cachedEntities = redisCacheRepository.getCartProductListItemsBulk(productIds);

        // 캐시 map 생성
        Map<UUID, ProductListItem> cachedMap = cachedEntities.stream()
                .map(ProductListItemMapper::toDomain)
                .collect(Collectors.toMap(
                        ProductListItem::getId,
                        item -> item
                ));

        // missing productId
        List<UUID> missIds = productIds.stream().filter(id -> !cachedMap.containsKey(id)).toList();

        if (!missIds.isEmpty()) {
            // DB 조회
            List<ProductListItem> productListItemsFromOrigin = productListItemOriginService.getCartProductListItemsBulkFromOrigin(missIds);
            // 엔터티로 변경
            List<ProductListItemEntity> entitiesToCache = productListItemsFromOrigin.stream().map(ProductListItemMapper::toEntity).toList();
            // 캐시 저장
            redisCacheRepository.putCartProductListItemsBulk(entitiesToCache, Duration.ofSeconds(60));
            productListItemsFromOrigin.forEach(item -> cachedMap.put(item.getId(), item));
        }

        return productIds.stream()
                .map(cachedMap::get)
                .toList();
    }
}
