package profect.group1.goormdotcom.kafka.event;

import java.util.UUID;

/**
 * 재고 롤백 완료 이벤트
 */
public record StockRollbackCompletedEvent(
    UUID orderId
) {
}

