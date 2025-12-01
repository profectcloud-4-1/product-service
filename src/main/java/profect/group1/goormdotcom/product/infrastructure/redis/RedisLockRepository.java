package profect.group1.goormdotcom.product.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
public class RedisLockRepository extends RedisConnectionGuard {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String UNLOCK_SCRIPT =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('DEL', KEYS[1]) " +
            "else " +
                    "return 0 " +
            "end";

    private final RedisScript<Long> unlockScript =
            RedisScript.of(UNLOCK_SCRIPT, Long.class);

    public String tryLock(String lockKey, Duration ttl) {
        String token = UUID.randomUUID().toString();

        Boolean success = callWithConnectionFallback(
            "GET_LOCK",
            () -> redisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl),
            false
        );

        return Boolean.TRUE.equals(success) ? token : null;
    }

    public Map<String, String> tryLocksBulk(List<String> lockKeys, Duration ttl) {
        Map<String, String> tokenMap = new HashMap<>();
        for (String lockKey : lockKeys) {tokenMap.put(lockKey, UUID.randomUUID().toString());};

        List<Object> results = executeWithConnectionGuard(
          "GET_LOCK_BULK",
            () -> redisTemplate.executePipelined((RedisCallback<List<Object>>) connection -> {
                        RedisSerializer<String> keySer = redisTemplate.getStringSerializer();
                        RedisSerializer<String> valSer = redisTemplate.getStringSerializer();

                        for (Map.Entry<String, String> entry : tokenMap.entrySet()) {
                            byte[] k = keySer.serialize(entry.getKey());
                            byte[] v = valSer.serialize(entry.getValue());
                            connection.stringCommands().set(k, v, Expiration.milliseconds(ttl.getSeconds()), RedisStringCommands.SetOption.ifAbsent());
                        }

                        return null;
                    }
            ),
            List.of()
        );

        Map<String, String> locked = new HashMap<>();
        for (int i = 0; i <results.size(); ++i) {
            Object r = results.get(i);
            if (Boolean.TRUE.equals(r) || "OK".equals(r)) {
                locked.put(lockKeys.get(i), tokenMap.get(lockKeys.get(i)));
            }
        }
        return locked;
    }

    public void unlock(String lockKey, String token) {
        if (token == null) { return ; }
        executeWithConnectionGuard(
            "UNLOCK",
                () -> redisTemplate.execute(
                        unlockScript,
                        Collections.singletonList(lockKey),
                        token
                ),
                null
        );

    }

    public void unLocksBulk(Map<String, String> lockTokenMap) {
        if (lockTokenMap == null || lockTokenMap.isEmpty()) {
            return;
        }

        byte[] unlock_script_bytes = UNLOCK_SCRIPT.getBytes(StandardCharsets.UTF_8);
        executeWithConnectionGuard(
            "UNLOCK_BULK",
                () -> redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                            RedisSerializer<String> keySer = redisTemplate.getStringSerializer();
                            RedisSerializer<String> valSer = redisTemplate.getStringSerializer();

                            for (Map.Entry<String, String> entry : lockTokenMap.entrySet()) {

                                byte[] k = keySer.serialize(entry.getKey());
                                byte[] v = valSer.serialize(entry.getValue());

                                connection.scriptingCommands().eval(
                                        unlock_script_bytes,
                                        ReturnType.INTEGER,
                                        1,
                                        k, v
                                );
                            }
                            return null;
                        }
                ),
                null
        );
    }
}
