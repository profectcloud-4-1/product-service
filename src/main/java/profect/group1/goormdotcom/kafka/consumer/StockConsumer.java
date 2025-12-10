package profect.group1.goormdotcom.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import profect.group1.goormdotcom.kafka.common.EventEnvelope;
import profect.group1.goormdotcom.kafka.common.EventManager;
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
    private final EventManager eventManager;

    @KafkaListener(
        topics = "stock-service-topic",
        groupId = "stock-service-cg",
        containerFactory = "stockKafkaListenerContainerFactory"
    )
    public void handleEvent(String message) {
        try{
            EventEnvelope eventEnvelope = objectMapper.readValue(message, EventEnvelope.class);
            switch (eventEnvelope.getEventType()) {

                case "StockRollbackRequested":
                    StockRollbackRequestedEvent stockRollbackCompletedEvent = eventManager.unwrap(eventEnvelope, StockRollbackRequestedEvent.class);
                    // Map에서 필드 추출
                    UUID orderId = stockRollbackCompletedEvent.getOrderId();
                    // items 필드 추출 (List<Map> 형태)
                    List<StockRollbackRequestedEvent.StockItem> items = stockRollbackCompletedEvent.getItems();
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
                    log.info("재고 롤백 완료에 따라 주문 상태를 FAILED로 갱신: orderId={}", stockRollbackCompletedEvent.getOrderId());
                    break;

                default:
                    throw new IllegalStateException("Unexpected event type: " + eventEnvelope.getEventType());
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error parsing event envelope from Kafka message");
        }
    }
}