package profect.group1.goormdotcom.product.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummary {
    private UUID id;
    private String name;
    private int price;
    private UUID mainImageId;
    private ProductImage mainImage;
    private ProductStatus status;
}
