package profect.group1.goormdotcom.kafka.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 주문 취소 또는 결제 실패 시 재고 서비스에 재고 롤백을 요청하기 위한 도메인 이벤트.
 */
public record StockRollbackRequestedEvent(
        UUID orderId,
        List<StockItem> items,
        Instant occurredAt
) {
    /**
     * 재고 롤백에 필요한 상품 정보.
     */
    public record StockItem(
            UUID productId,
            int quantity
    ) {
    }
}

