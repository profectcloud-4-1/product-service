package profect.group1.goormdotcom.kafka.common;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventEnvelope {
    private UUID eventId;
    private String eventType;
    private String aggregateType;
    private LocalDateTime occuredAt;
    private long version;
    private String source;
    // 이벤트 정보
    private String payload;

    public void setPayload(String payload) {
        this.payload = payload;
    }
}

