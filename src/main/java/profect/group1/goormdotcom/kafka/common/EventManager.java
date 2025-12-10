package profect.group1.goormdotcom.kafka.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventManager {
    private final ObjectMapper mapper;

    private EventEnvelope createEventEnvelope(String eventType, String aggregateType, LocalDateTime occuredAt, long version, String source) {
        UUID eventId = UUID.randomUUID();
        return EventEnvelope.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType(aggregateType)
                .occuredAt(occuredAt)
                .version(version)
                .source(source)
                .build();
    }

    public EventEnvelope wrap(Object payload, String eventType, String aggregateType,  LocalDateTime occuredAt, long version, String source) {
        EventEnvelope eventEnvelope = createEventEnvelope(eventType, aggregateType, occuredAt, version, source);
        String payloadString;
        try {
            payloadString = mapper.writeValueAsString(payload);
            eventEnvelope.setPayload(payloadString);
            return eventEnvelope;
        } catch (JsonProcessingException e) {
            log.info("Could not serialize payload", e);
            throw new IllegalStateException("Could not serialize payload", e);
        }
    }

    public <T> T unwrap(EventEnvelope eventEnvelope, Class<T> type) {
        String payloadString = eventEnvelope.getPayload();
        try {
            return mapper.readValue(payloadString, type);
        } catch (JsonProcessingException e) {
            log.info("Could not deserialize payload", e);
            throw new IllegalStateException("Could not deserialize payload", e);
        }
    }
}
