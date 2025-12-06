package profect.group1.goormdotcom.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import profect.group1.goormdotcom.kafka.event.StockRollbackRequestedEvent;
import profect.group1.goormdotcom.stock.service.AdjustStockService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockConsumer {
    
    private final AdjustStockService adjustStockService;
    private final ObjectMapper objectMapper;

    /**
     * 재고 롤백 요청 이벤트를 Kafka 토픽에서 수신하여 재고 복구 처리
     * Order-Service에서 발행한 메시지를 Map 형태로 받아서 동적으로 파싱
     * 
     * @param message Map 형태로 자동 역직렬화됨 (JsonDeserializer 설정으로 인해)
     */
    @KafkaListener(
        topics = "stock-rollback-requested-topic",
        groupId = "stock-rollback-requested-cg"
    )
    public void handleStockRollbackRequestedEvent(String message) {
        try {
            log.info("재고 롤백 요청 이벤트 수신: message={}", message);
            StockRollbackRequestedEvent event  = objectMapper.readValue(message, StockRollbackRequestedEvent.class);
            // Map에서 필드 추출
            UUID orderId = event.orderId();
            
            // items 필드 추출 (List<Map> 형태)
            List<StockRollbackRequestedEvent.StockItem> items = event.items();
            
            log.info("재고 롤백 요청 이벤트 파싱 완료: orderId={}, items={}", orderId, items != null ? items.size() : 0);
            
            // List<Map>을 Map<UUID, Integer>로 변환
            if (items != null && !items.isEmpty()) {
                Map<UUID, Integer> requestedQuantityMap = items.stream()
                    .collect(Collectors.toMap(
                            StockRollbackRequestedEvent.StockItem::productId,
                            StockRollbackRequestedEvent.StockItem::quantity
                    ));
                // 재고 복구 로직 실행 (차감했던 재고를 다시 증가)
                // orderId를 전달하여 트랜잭션 완료/롤백 시 이벤트가 자동 발행됨
                adjustStockService.tryIncreaseStocks(requestedQuantityMap, orderId);

                log.info("재고 롤백 처리 완료: orderId={}, products={}",
                        orderId, requestedQuantityMap.keySet());
            } else {
                log.info("롤백 요청된 재고가 없습니다.");
            }

        } catch (Exception e) {
            log.error("재고 롤백 처리 실패: message={}", message, e);
            // 예외가 발생하면 AdjustStockService에서 이미 StockRollbackFailedEvent가 발행됨
            // 여기서는 로깅만 하고 예외를 다시 던지지 않음 (이미 이벤트 발행 완료)
            // TODO: DLQ(Dead Letter Queue) 처리 추가 고려
        }
    }
}