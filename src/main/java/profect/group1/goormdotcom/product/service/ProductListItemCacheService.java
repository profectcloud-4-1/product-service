package profect.group1.goormdotcom.product.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.StockClient;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.RedisLockRepository;
import profect.group1.goormdotcom.product.service.utils.ImageUrlGenerator;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductListItemCacheService {

    private final ProductListItemDBService productListItemDBService;

    private final RedisLockRepository redisLockRepository;
    private final CacheManager cacheManager;

    public ProductListItem getCartProductListItem(UUID productId) { // 캐시 조회 로직
            Cache cache = cacheManager.getCache("product-list-item:cart");

            if (cache == null) {
                log.info("Cache not found");
                return productListItemDBService.getCartProductListItemFromOrigin(productId);
            }

            ProductListItem cachedItem;
            try {
                cachedItem = cache.get(productId, ProductListItem.class);
            } catch (RedisConnectionFailureException e) {
                log.error("Cache connection failure : DB fallback");
                return productListItemDBService.getCartProductListItemFromOrigin(productId);
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
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }

                    // 더블 체크
                    cachedItem = cache.get(productId, ProductListItem.class);
                    if (cachedItem != null) {
                        log.info("Product list item {} cache hit in double check", productId);
                        return cachedItem;
                    }
                }

                log.info("Product list item {} get lock", productId);
                ProductListItem productListItem = productListItemDBService.getCartProductListItemFromOrigin(productId);
                cache.put(productId, productListItem);
                return productListItem;
            } finally {
                Boolean result = redisLockRepository.unlock(lockKey);
                if (result) {
                    log.info("Product list item {} cache unlock", productId);
                }
            }
    }
}
