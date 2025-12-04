package profect.group1.goormdotcom.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import profect.group1.goormdotcom.kafka.event.StockRollbackCompletedEvent;
import profect.group1.goormdotcom.kafka.event.StockRollbackFailedEvent;
import profect.group1.goormdotcom.stock.repository.StockRepository;
import profect.group1.goormdotcom.stock.repository.entity.StockEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Getter
@Service
@RequiredArgsConstructor
public class AdjustStockService {

    private final StockRepository stockRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void tryDecreaseStocks(Map<UUID, Integer> requestedQuantityMap) {
        for (UUID productId: requestedQuantityMap.keySet()) {
            Optional<StockEntity> stockEntity = stockRepository.findByProductId(productId);

            if (stockEntity.isEmpty()) {
                throw new IllegalArgumentException("Product not found");
            }
            StockEntity entity = stockEntity.get();
            int requestedStockQuantity = requestedQuantityMap.get(productId);
            entity.decreaseQuantity(requestedStockQuantity);
            stockRepository.save(entity);
        }
    }

    /**
     * 재고 증가 처리 (재고 롤백용)
     * 
     * 🔑 핵심 동작 원리:
     * 1. 이 메서드는 @Transactional로 트랜잭션 내에서 실행됨
     * 2. eventPublisher.publishEvent()를 호출하면 이벤트를 발행하지만, 
     *    실제로 EventHandler가 실행되는 시점은 트랜잭션이 완료된 후임
     * 3. @TransactionalEventListener의 phase에 따라:
     *    - AFTER_COMMIT: 트랜잭션이 성공적으로 커밋된 후 → StockRollbackCompletedEvent 처리
     *    - AFTER_ROLLBACK: 트랜잭션이 롤백된 후 → StockRollbackFailedEvent 처리
     * 
     * @param requestedQuantityMap 재고 증가할 상품과 수량
     * @param orderId 주문 ID (이벤트 발행용, null이면 이벤트 발행 안 함)
     */
    @Transactional
    public void tryIncreaseStocks(Map<UUID, Integer> requestedQuantityMap, UUID orderId) {
        try {
            // 재고 증가 로직 실행
            for (UUID productId: requestedQuantityMap.keySet()) {
                Optional<StockEntity> stockEntity = stockRepository.findByProductId(productId);

                if (stockEntity.isEmpty()) {
                    throw new IllegalArgumentException("Product not found: " + productId);
                }
                StockEntity entity = stockEntity.get();
                int requestedStockQuantity = requestedQuantityMap.get(productId);
                entity.increaseQuantity(requestedStockQuantity);
                stockRepository.save(entity);
            }
            
            // ✅ 성공 케이스
            // 이벤트를 발행 (아직 실행 안 됨, 트랜잭션 커밋 후에 처리됨)
            if (orderId != null) {
                eventPublisher.publishEvent(new StockRollbackCompletedEvent(orderId));
                // 이 시점에는 EventHandler가 실행되지 않음!
                // 트랜잭션이 정상적으로 커밋되면 → AFTER_COMMIT에서 실행됨
            }
            
        } catch (Exception e) {
            // ❌ 실패 케이스
            // 이벤트를 발행 (아직 실행 안 됨, 트랜잭션 롤백 후에 처리됨)
            if (orderId != null) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : "Stock rollback failed";
                String errorType = e.getClass().getSimpleName();
                eventPublisher.publishEvent(new StockRollbackFailedEvent(orderId, errorMessage, errorType));
                // 이 시점에는 EventHandler가 실행되지 않음!
                // 트랜잭션이 롤백되면 → AFTER_ROLLBACK에서 실행됨
            }
            
            // 예외를 다시 던져서 트랜잭션 롤백 보장
            throw e;
        }
    }
    
    /**
     * 기존 메서드 유지 (호환성)
     */
    @Transactional
    public void tryIncreaseStocks(Map<UUID, Integer> requestedQuantityMap) {
        tryIncreaseStocks(requestedQuantityMap, null);
    }
}