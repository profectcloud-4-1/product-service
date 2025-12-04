package profect.group1.goormdotcom.kafka.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import profect.group1.goormdotcom.kafka.StockProducer;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventHandler {
    
    private final StockProducer stockProducer;

    /**
     * 재고 롤백 성공 이벤트 처리
     * 트랜잭션이 정상적으로 커밋된 후에만 실행됨
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleStockRollbackCompletedEvent(StockRollbackCompletedEvent event) {
        log.info("재고 롤백 완료 이벤트 수신: orderId={}", event.orderId());
        stockProducer.sendStockRollbackCompletedEvent("order-service-topic", event);
    }

    /**
     * 재고 롤백 실패 이벤트 처리
     * 트랜잭션이 롤백된 후에만 실행됨
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Async
    public void handleStockRollbackFailedEvent(StockRollbackFailedEvent event) {
        log.warn("재고 롤백 실패 이벤트 수신: orderId={}, error={}", event.orderId(), event.errorMessage());
        stockProducer.sendStockRollbackFailedEvent("order-service-topic", event);
    }
}