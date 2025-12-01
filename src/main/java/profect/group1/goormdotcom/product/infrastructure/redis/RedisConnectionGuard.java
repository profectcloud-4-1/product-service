package profect.group1.goormdotcom.product.infrastructure.redis;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

import java.util.function.Supplier;

@Slf4j
public abstract class RedisConnectionGuard {

    protected <T> T callWithConnectionFallback(String opName, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (RedisConnectionFailureException |
                 RedisConnectionException |
                 RedisCommandTimeoutException |
                 RedisSystemException e) {
            log.warn("[REDIS][{}] failed -> fallback. msg={}", opName, e.getMessage());
            return fallback;
        }
    }

    protected void runWithConnectionGuard(String opName, Runnable action) {
        try {
            action.run();
        } catch (RedisConnectionFailureException |
                 RedisConnectionException |
                 RedisCommandTimeoutException |
                 RedisSystemException e) {
            log.warn("[REDIS][{}] failed -> ignored. msg={}", opName, e.getMessage());
        }
    }

    protected <T> T executeWithConnectionGuard(String opName, Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (RedisConnectionFailureException |
                 RedisConnectionException |
                 RedisCommandTimeoutException |
                 RedisSystemException e) {
            log.warn("[REDIS][{}] failed -> fallback. msg={}", opName, e.getMessage());
            return fallback;
        }
    }
}
