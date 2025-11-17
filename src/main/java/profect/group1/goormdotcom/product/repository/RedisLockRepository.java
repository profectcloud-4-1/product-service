package profect.group1.goormdotcom.product.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisLockRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public Boolean lock(String lockKey) {
        return redisTemplate
                .opsForValue()
                .setIfAbsent(lockKey, "lock", Duration.ofMillis(3000));
    }

    public Boolean unlock(String lockKey) {
        return redisTemplate.delete(lockKey);
    }

}
