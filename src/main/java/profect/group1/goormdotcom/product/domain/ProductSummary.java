package profect.group1.goormdotcom.product.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.RedisHash;
import profect.group1.goormdotcom.product.domain.param.ProductSummaryUpdateRequest;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummary {
    private UUID id;
    private String name;
    private int price;
    private String mainImageUrl;
    private String status;

    public void update(ProductSummaryUpdateRequest req) {
        if (req.name() != null) this.name = req.name();
        if (req.price() > 0) this.price = req.price();
        if (req.mainImageUrl() != null) this.mainImageUrl = req.mainImageUrl();
        if (req.status() != null) this.status = req.status();
    }
}
