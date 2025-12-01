package profect.group1.goormdotcom.product.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisConnectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheRepository extends RedisConnectionGuard {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CART_PRODUCT_KEY_PREFIX = "product-list-item:cart:";

    private String buildCartProductKey(UUID productId) {
        return CART_PRODUCT_KEY_PREFIX + productId;
    }

    public List<ProductListItemEntity> getCartProductListItemsBulk(List<UUID> productIds) {
        // Redis 키 리스트 생성
        List<String> keys = productIds.stream()
                .map(this::buildCartProductKey)
                .toList();

        // MGET 실행
        List<String> values = callWithConnectionFallback(
            "GET_CART_BULK",
            () -> redisTemplate.opsForValue().multiGet(keys),
            null
        );

        if (values == null || values.isEmpty()) {
            return List.of();
        }

        // JSON → ProductListItemEntity 역직렬화
        List<ProductListItemEntity> result = new ArrayList<>(values.size());

        for (String value : values) {
            if (value == null) {
                continue;
            }
            try {
                ProductListItemEntity item = objectMapper.readValue(value, ProductListItemEntity.class);
                result.add(item);
            } catch (Exception e) {
                 log.warn("Failed to deserialize cart product item. value={}", value, e);
            }
        }

        return result;
    }

    public void putCartProductListItemsBulk(List<ProductListItemEntity> entities, Duration ttl) {
        // Redis 키 리스트 생성
        Map<String, String> cachingMap = new HashMap<>();
        for (ProductListItemEntity entity : entities) {
            String cacheKey = buildCartProductKey(entity.getId());
            try {
                String cacheValue = objectMapper.writeValueAsString(entity);
                cachingMap.put(cacheKey, cacheValue);
            } catch (JsonProcessingException e) {
                 log.warn("Failed to serialize ProductListItemEntity. id={}", entity.getId(), e);
            }
        }

        if (cachingMap.isEmpty()) {
            return ;
        }

        // MSET 실행
        executeWithConnectionGuard(
            "PUT_CART_BULK",
            () -> redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                var keySerializer = redisTemplate.getStringSerializer();
                var valueSerializer = redisTemplate.getStringSerializer();

                cachingMap.forEach((key, json) -> {
                    byte[] k = keySerializer.serialize(key);
                    byte[] v = valueSerializer.serialize(json);
                    if (k != null && v != null) {
                        connection.stringCommands()
                                .setEx(k, ttl.getSeconds(), v);
                    }
                });
                return null;
            }),
            null
        );
    }
}