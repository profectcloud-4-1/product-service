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

import java.time.LocalDateTime;
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

            if (orderId != null) {
                LocalDateTime occuredAt = LocalDateTime.now();
                eventPublisher.publishEvent(new StockRollbackCompletedEvent(orderId, occuredAt));
            }
            
        } catch (Exception e) {
            if (orderId != null) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : "Stock rollback failed";
                String errorType = e.getClass().getSimpleName();
                LocalDateTime occuredAt = LocalDateTime.now();
                eventPublisher.publishEvent(new StockRollbackFailedEvent(orderId, errorMessage, errorType, occuredAt));
            }
            throw e;
        }
    }

    @Transactional
    public void tryIncreaseStocks(Map<UUID, Integer> requestedQuantityMap) {
        tryIncreaseStocks(requestedQuantityMap, null);
    }
}