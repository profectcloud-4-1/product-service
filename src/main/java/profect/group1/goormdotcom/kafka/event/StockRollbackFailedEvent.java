package profect.group1.goormdotcom.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class StockRollbackFailedEvent{
    private UUID orderId;
    private String errorMessage;
    private String errorType;
    private LocalDateTime occurredAt;
}

