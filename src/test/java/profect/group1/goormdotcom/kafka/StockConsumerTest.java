package profect.group1.goormdotcom.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import profect.group1.goormdotcom.stock.repository.StockRepository;
import profect.group1.goormdotcom.stock.repository.entity.StockEntity;
import profect.group1.goormdotcom.stock.service.AdjustStockService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockConsumer 단위 테스트")
class StockConsumerTest {

    @InjectMocks
    private StockConsumer stockConsumer;

    @Mock
    private AdjustStockService adjustStockService;

    @Mock
    private StockRepository stockRepository;

    /**
     * 테스트용 재고 롤백 요청 메시지 생성 헬퍼 메서드
     * Order-Service에서 발행하는 메시지 형식과 동일하게 매핑
     * 
     * @param orderId 주문 ID
     * @param items 상품 ID와 수량 리스트
     * @return Kafka 메시지 (Map<String, Object>)
     */
    private Map<String, Object> createStockRollbackRequestedMessage(UUID orderId, List<Map<String, Object>> items) {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", orderId.toString());
        message.put("items", items);
        return message;
    }

    /**
     * 테스트용 상품 아이템 생성 헬퍼 메서드
     * 
     * @param productId 상품 ID
     * @param quantity 수량
     * @return 상품 아이템 (Map<String, Object>)
     */
    private Map<String, Object> createItem(UUID productId, int quantity) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId.toString());
        item.put("quantity", quantity);
        return item;
    }

    @Test
    @DisplayName("성공 - Map 메시지를 수신하여 재고 롤백 처리")
    void handleStockRollbackRequestedEvent_Success() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        
        // 테스트용 재고 수량 (임의로 지정)
        int quantity1 = 10;
        int quantity2 = 20;

        // Order-Service에서 발행하는 형태로 Map 메시지 생성
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createItem(productId1, quantity1));
        items.add(createItem(productId2, quantity2));
        
        Map<String, Object> message = createStockRollbackRequestedMessage(orderId, items);

        System.out.println("========================================");
        System.out.println("🔍 [테스트 디버깅] Consumer 메서드 호출 시작:");
        System.out.println("  OrderId: " + orderId);
        System.out.println("  Product1: " + productId1 + " (수량: " + quantity1 + ")");
        System.out.println("  Product2: " + productId2 + " (수량: " + quantity2 + ")");
        System.out.println("========================================");

        // when - Consumer 메서드 직접 호출
        stockConsumer.handleStockRollbackRequestedEvent(message);

        System.out.println("========================================");
        System.out.println("✅ [테스트 디버깅] Consumer 메서드 호출 완료");
        System.out.println("========================================");

        // then - AdjustStockService의 tryIncreaseStocks가 호출되었는지 확인
        verify(adjustStockService, times(1)).tryIncreaseStocks(any(Map.class), eq(orderId));
        
        System.out.println("========================================");
        System.out.println("✅ [테스트 디버깅] 재고 롤백 처리 검증 완료!");
        System.out.println("  AdjustStockService.tryIncreaseStocks() 호출 확인");
        System.out.println("========================================");
    }

    @Test
    @DisplayName("실패 - 존재하지 않는 상품에 대한 롤백 요청 시 예외 처리")
    void handleStockRollbackRequestedEvent_ProductNotFound() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID nonExistentProductId = UUID.randomUUID();
        
        // 테스트용 재고 수량 (임의로 지정)
        int quantity = 10;

        // Order-Service에서 발행하는 형태로 Map 메시지 생성
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(createItem(nonExistentProductId, quantity));
        
        Map<String, Object> message = createStockRollbackRequestedMessage(orderId, items);

        // AdjustStockService에서 예외 발생하도록 설정
        doThrow(new IllegalArgumentException("Product not found: " + nonExistentProductId))
            .when(adjustStockService).tryIncreaseStocks(any(Map.class), eq(orderId));

        System.out.println("========================================");
        System.out.println("🔍 [테스트 디버깅] 예외 발생 시나리오 테스트:");
        System.out.println("  OrderId: " + orderId);
        System.out.println("  ProductId: " + nonExistentProductId + " (존재하지 않음)");
        System.out.println("========================================");

        // when - Consumer 메서드 직접 호출 (예외 발생 예상)
        stockConsumer.handleStockRollbackRequestedEvent(message);

        System.out.println("========================================");
        System.out.println("✅ [테스트 디버깅] Consumer 메서드 호출 완료");
        System.out.println("  (예외가 발생했지만 Consumer에서 로깅만 하고 예외를 다시 던지지 않음)");
        System.out.println("========================================");

        // then - AdjustStockService의 tryIncreaseStocks가 호출되었는지 확인
        verify(adjustStockService, times(1)).tryIncreaseStocks(any(Map.class), eq(orderId));
        
        System.out.println("========================================");
        System.out.println("✅ [테스트 디버깅] 예외 처리 검증 완료!");
        System.out.println("  AdjustStockService.tryIncreaseStocks() 호출 확인");
        System.out.println("  예외가 발생했지만 Consumer에서 예외를 다시 던지지 않음 확인");
        System.out.println("========================================");
    }
}

