package profect.group1.goormdotcom.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import profect.group1.goormdotcom.kafka.event.StockRollbackCompletedEvent;
import profect.group1.goormdotcom.kafka.event.StockRollbackFailedEvent;


@Slf4j
@Component
@RequiredArgsConstructor
public class StockProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 재고 롤백 완료 이벤트를 Kafka 토픽으로 발행
     */
    public void sendStockRollbackCompletedEvent(String topic, StockRollbackCompletedEvent event) {
        kafkaTemplate.send(topic, event);
        log.info("재고 롤백 완료 이벤트 발행: topic={}, orderId={}", topic, event.orderId());
    }

    /**
     * 재고 롤백 실패 이벤트를 Kafka 토픽으로 발행
     */
    public void sendStockRollbackFailedEvent(String topic, StockRollbackFailedEvent event) {
        kafkaTemplate.send(topic, event);
        log.warn("재고 롤백 실패 이벤트 발행: topic={}, orderId={}, error={}", topic, event.orderId(), event.errorMessage());
    }
}