package profect.group1.goormdotcom.product.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import org.springframework.cache.CacheManager;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.domain.ProductStatus;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.StockClient;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.dto.StockResponseDto;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.RedisLockRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.repository.mapper.ProductMapper;
import profect.group1.goormdotcom.product.service.utils.ImageUrlGenerator;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductListItemCacheService {

    private final ProductRepository productRepository;

    private final StockClient stockClient;

    private final ImageUrlGenerator imageUrlGenerator;

    private final RedisLockRepository redisLockRepository;
    private final CacheManager cacheManager;

    public ProductListItem getCartProductListItem(UUID productId) { // 캐시 조회 로직
            Cache cache = cacheManager.getCache("product-list-item:cart");

            if (cache == null) {
                log.info("Cache not found");
                return getCartProductListItemFromOrigin(productId);
            }

            ProductListItem cachedItem;
            try {
                cachedItem = cache.get(productId, ProductListItem.class);
            } catch (RedisConnectionFailureException e) {
                log.error("Cache connection failure : DB fallback");
                return getCartProductListItemFromOrigin(productId);
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
                ProductListItem productListItem = getCartProductListItemFromOrigin(productId);
                cache.put(productId, productListItem);
                return productListItem;
            } finally {
                Boolean result = redisLockRepository.unlock(lockKey);
                if (result) {
                    log.info("Product list item {} cache unlock", productId);
                }
            }
    }


    // 아래 Fallback 로직 필요할듯
    @RateLimiter(name = "productOrigin")
    @Bulkhead(name = "productOrigin", type = Bulkhead.Type.SEMAPHORE)
    public ProductListItem getCartProductListItemFromOrigin(UUID productId) { // DB 조회 로직
        ProductListItem productListItem;

        // 1. 상품 존재 여부 파악 -> 없으면 name에 존재하지 않는 상품 표시 보내기
        Optional<ProductEntity> productEntity = productRepository.findByIdIncludingDeleted(productId);

        String imageUrl;
        if (productEntity.isPresent()) {
            ProductEntity entity = productEntity.get();

            // 첫번째 이미지를 카트 화면에서 띄울 메인 이미지로 선택
            imageUrl = imageUrlGenerator.generateProductImageUrl(entity.getMainImageId());

            // 2. 삭제 여부 확인
            if (entity.getDeletedAt() != null) {
                productListItem = ProductMapper.toProductListItem(entity, imageUrl, ProductStatus.NOT_EXIST);
            } else {
                // 3. 재고 존재 여부 파악
                ApiResponse<StockResponseDto> response = stockClient.getStock(productId);
                if (response.getResult() == null) {

                    // 재고 값을 못받아오면 존재 하지 않는 상품으로 봄
                    productListItem = ProductMapper.toProductListItem(entity, imageUrl, ProductStatus.NOT_EXIST);
                } else if (response.getResult().stockQuantity() <= 0) {
                    // 재고값이 0 이하인 경우 매진
                    productListItem = ProductMapper.toProductListItem(entity, imageUrl, ProductStatus.SOLD_OUT);
                } else {
                    productListItem = ProductMapper.toProductListItem(entity, imageUrl, ProductStatus.AVAILABLE);
                }
            }

        } else {
            imageUrl = imageUrlGenerator.generateDefaultImageUrl();
            productListItem = new ProductListItem(
                    productId,null, 0, imageUrl, ProductStatus.NOT_EXIST.getValue()
            );
        }
        return productListItem;
    }
}
