package profect.group1.goormdotcom.kafka.event;

import java.util.UUID;

/**
 * 재고 롤백 실패 이벤트
 */
public record StockRollbackFailedEvent(
    UUID orderId,
    String errorMessage,
    String errorType
) {
}

