package profect.group1.goormdotcom.product.repository.entity;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.redis.core.RedisHash;
import profect.group1.goormdotcom.product.domain.ProductImage;
import profect.group1.goormdotcom.product.domain.ProductStatus;

import java.util.UUID;

@Getter
@RedisHash("productSummary")
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummary {

    @Id
    private String id;
    private String name;
    private int price;
    private String mainImageUrl;
    private String status;
}
