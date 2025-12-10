package profect.group1.goormdotcom.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import profect.group1.goormdotcom.kafka.common.EventEnvelope;
import profect.group1.goormdotcom.kafka.common.EventManager;
import profect.group1.goormdotcom.kafka.event.StockRollbackCompletedEvent;
import profect.group1.goormdotcom.kafka.event.StockRollbackFailedEvent;

import java.time.LocalDateTime;


@Slf4j
@Component
@RequiredArgsConstructor
public class StockProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final EventManager eventManager;

    public void send(
            String topic,
            String key,
            String eventType,
            String aggregateType,
            LocalDateTime occuredAt,
            long version,
            String source,
            Object eventPayload) {

        EventEnvelope eventEnvelope = eventManager.wrap(eventPayload, eventType, aggregateType, occuredAt, version, source);
        kafkaTemplate.send(topic, key, eventEnvelope);
        log.info("Kafka 메시지 발행 완료: topic={} payload={}", topic, eventEnvelope.getPayload());
    }
}