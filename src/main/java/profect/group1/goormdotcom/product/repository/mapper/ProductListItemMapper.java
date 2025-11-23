package profect.group1.goormdotcom.product.repository.mapper;

import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.repository.entity.ProductListItemEntity;

public class ProductListItemMapper {
    public static ProductListItem toDomain(ProductListItemEntity productListItemEntity) {
        return new ProductListItem(
                productListItemEntity.getId(),
                productListItemEntity.getName(),
                productListItemEntity.getPrice(),
                productListItemEntity.getMainImageUrl(),
                productListItemEntity.getStatus()
        );
    }

    public static ProductListItemEntity toEntity(ProductListItem productListItem) {
        return new ProductListItemEntity(
                productListItem.getId(),
                productListItem.getName(),
                productListItem.getPrice(),
                productListItem.getMainImageUrl(),
                productListItem.getStatus()
        );

    }
}
