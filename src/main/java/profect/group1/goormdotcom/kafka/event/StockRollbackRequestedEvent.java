package profect.group1.goormdotcom.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class StockRollbackRequestedEvent{
    private UUID orderId;
    private List<StockItem> items;

    public record StockItem(
            UUID productId,
            int quantity
    ) {
    }
}

