package profect.group1.goormdotcom.product.repository.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.RedisHash;

import java.util.UUID;

@Getter
@RedisHash("productSummary")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSummaryEntity {

    @Id
    private UUID id;
    private String name;
    private int price;
    private String mainImageUrl;
    private String status;
}
