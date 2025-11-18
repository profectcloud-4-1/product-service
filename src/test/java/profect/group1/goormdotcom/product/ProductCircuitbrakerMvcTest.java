package profect.group1.goormdotcom.product;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import profect.group1.goormdotcom.product.controller.internal.v1.ProductInternalController;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.service.ProductListItemService;
import profect.group1.goormdotcom.product.service.ProductService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductCircuitbrakerMvcTest {

    private MockMvc mockMvc;

    @Mock private ProductService productService;
    @Mock private ProductListItemService productListItemService;
    @BeforeEach
    void setup() {
        ProductInternalController controller = newController();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new TestAdvice())
                .build();
    }

    private ProductInternalController newController() {
        try {
            var ctor = ProductInternalController.class.getDeclaredConstructor(ProductService.class, ProductListItemService.class);
            ctor.setAccessible(true);
            return ctor.newInstance(productService, productListItemService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @ControllerAdvice
    static class TestAdvice {
        @ExceptionHandler(RequestNotPermitted.class)
        public ResponseEntity<Void> handleRateLimited(RequestNotPermitted ex) {
            return ResponseEntity.status(503).build();
        }

        @ExceptionHandler(BulkheadFullException.class)
        public ResponseEntity<Void> handleBulkhead(BulkheadFullException ex) {
            return ResponseEntity.status(503).build();
        }
    }

    @Test
    @DisplayName("RateLimiter 예외 발생 시 503 매핑")
    void ratelimiter_exception_maps_to_503() throws Exception {
        UUID id = UUID.randomUUID();
        String url = "/internal/v1/product/cart?product-ids=" + id;

        AtomicInteger call = new AtomicInteger(0);
        when(productListItemService.getCartProducts(anyList())).thenAnswer(inv -> {
            if (call.incrementAndGet() <= 2) {
                return List.of(new ProductListItem(id, "A", 100, "u", "AVAILABLE"));
            } else {
                RateLimiter rl = RateLimiterRegistry.ofDefaults().rateLimiter("test-rl");
                throw RequestNotPermitted.createRequestNotPermitted(rl);
            }
        });

        mockMvc.perform(get(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Bulkhead 예외 발생 시 503 매핑")
    void bulkhead_exception_maps_to_503() throws Exception {
        UUID id = UUID.randomUUID();
        String url = "/internal/v1/product/cart?product-ids=" + id;

        // 두 번째 호출에서만 BulkheadFullException 발생하도록 제어
        AtomicBoolean first = new AtomicBoolean(true);
        when(productListItemService.getCartProducts(anyList())).thenAnswer(inv -> {
            if (first.getAndSet(false)) {
                return List.of(new ProductListItem(id, "A", 100, "u", "AVAILABLE"));
            } else {
                Bulkhead bh = BulkheadRegistry.ofDefaults().bulkhead("test-bh");
                throw BulkheadFullException.createBulkheadFullException(bh);
            }
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        var f1 = pool.submit(() -> mockMvc.perform(get(url)).andReturn().getResponse().getStatus());
        var f2 = pool.submit(() -> mockMvc.perform(get(url)).andReturn().getResponse().getStatus());
        int s1 = f1.get(2, TimeUnit.SECONDS);
        int s2 = f2.get(2, TimeUnit.SECONDS);
        pool.shutdownNow();

        boolean ok = (s1 == 200 && s2 == 503) || (s1 == 503 && s2 == 200);
        org.assertj.core.api.Assertions.assertThat(ok).isTrue();
    }
}
