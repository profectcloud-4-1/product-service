package profect.group1.goormdotcom.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {

        // key는 문자열
        RedisSerializationContext.SerializationPair<String> keySerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()
                );

        // value는 JSON (object mapper 설정은 따로 하지 않음.)
        RedisSerializationContext.SerializationPair<Object> valueSerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()
                );

        // 기본 config 설정
        RedisCacheConfiguration defaultConfig =
                RedisCacheConfiguration.defaultCacheConfig()
                        .serializeKeysWith(keySerializer)
                        .serializeValuesWith(valueSerializer)
                        .entryTtl(Duration.ofSeconds(60))   // 기본 TTL
                        .disableCachingNullValues();

        // productSummary 캐시 설정 추가
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("productSummary",
                defaultConfig.entryTtl(Duration.ofSeconds(60)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(
                    RuntimeException exception, Cache cache, Object key
            ) {
                log.warn("[CACHE][GET] 실패 – cache={}, key={}, msg={}",
                        cacheName(cache), key, exception.getMessage());
                // ❗ 예외를 다시 던지지 않는다
                // → @Cacheable 메서드 본문(DB/stock 로직) 실행됨
            }

            @Override
            public void handleCachePutError(
                    RuntimeException exception, Cache cache, Object key, Object value
            ) {
                log.warn("[CACHE][PUT] 실패 – cache={}, key={}, msg={}",
                        cacheName(cache), key, exception.getMessage());
                // 캐시에 못 넣어도 요청 자체는 성공해야 하니까 그냥 무시
            }

            @Override
            public void handleCacheEvictError(
                    RuntimeException exception, Cache cache, Object key
            ) {
                log.warn("[CACHE][EVICT] 실패 – cache={}, key={}, msg={}",
                        cacheName(cache), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(
                    RuntimeException exception, Cache cache
            ) {
                log.warn("[CACHE][CLEAR] 실패 – cache={}, msg={}",
                        cacheName(cache), exception.getMessage());
            }

            private String cacheName(Cache cache) {
                return cache != null ? cache.getName() : "unknown";
            }
        };
    }
}

