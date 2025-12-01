package profect.group1.goormdotcom.product.infrastructure.redis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductListItemEntity {
    private UUID id;
    private String name;
    private int price;
    private String mainImageUrl;
    private String status;
}
