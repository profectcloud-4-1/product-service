package profect.group1.goormdotcom.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import profect.group1.goormdotcom.kafka.event.StockRollbackCompletedEvent;
import profect.group1.goormdotcom.kafka.event.StockRollbackFailedEvent;
import profect.group1.goormdotcom.kafka.producer.StockProducer;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventHandler {
    
    private final StockProducer stockProducer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleStockRollbackCompletedEvent(StockRollbackCompletedEvent event) {
        log.info("재고 롤백 완료 이벤트 수신: orderId={}", event.getOrderId());
        stockProducer.send(
            "order-service-topic",
            event.getOrderId().toString(),
            "StockRollbackCompleted",
            "Stock",
            event.getOccurredAt(),
            1,
            "Stock",
            event
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Async
    public void handleStockRollbackFailedEvent(StockRollbackFailedEvent event) {
        log.warn("재고 롤백 실패 이벤트 수신: orderId={}, error={}", event.getOrderId(), event.getErrorMessage());
        stockProducer.send(
            "order-service-topic",
            event.getOrderId().toString(),
            "StockRollbackCompleted",
            "Stock",
            event.getOccurredAt(),
            1,
            "Stock",
            event
        );
    }
}