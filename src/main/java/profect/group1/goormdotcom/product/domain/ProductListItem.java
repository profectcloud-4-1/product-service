package profect.group1.goormdotcom.product.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductListItem {
    private UUID id;
    private String name;
    private int price;
    private String mainImageUrl;
}