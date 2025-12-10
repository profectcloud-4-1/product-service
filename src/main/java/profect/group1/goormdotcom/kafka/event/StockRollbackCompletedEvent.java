package profect.group1.goormdotcom.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class StockRollbackCompletedEvent{
    private UUID orderId;
    private LocalDateTime occurredAt;
}

