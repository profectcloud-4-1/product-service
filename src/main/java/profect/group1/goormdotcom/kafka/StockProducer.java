package profect.group1.goormdotcom.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 재고 롤백 완료 이벤트를 Kafka 토픽으로 발행
     */
    public void sendStockRollbackCompletedEvent(String topic, StockRollbackCompletedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, message);
            log.info("재고 롤백 완료 이벤트 발행: topic={}, orderId={}", topic, event.orderId());
        } catch (JsonProcessingException e) {
            log.error("Kafka 메시지 직렬화 실패: topic={}, orderId={}", topic, event.orderId(), e);
            throw new RuntimeException("Kafka 메시지 발행 실패", e);
        }
    }

    /**
     * 재고 롤백 실패 이벤트를 Kafka 토픽으로 발행
     */
    public void sendStockRollbackFailedEvent(String topic, StockRollbackFailedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, message);
            log.warn("재고 롤백 실패 이벤트 발행: topic={}, orderId={}, error={}", 
                    topic, event.orderId(), event.errorMessage());
        } catch (JsonProcessingException e) {
            log.error("재고 롤백 실패 이벤트 직렬화 실패: topic={}, orderId={}", topic, event.orderId(), e);
            throw new RuntimeException("Kafka 메시지 발행 실패", e);
        }
    }
}