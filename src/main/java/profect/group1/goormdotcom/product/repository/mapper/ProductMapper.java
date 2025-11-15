package profect.group1.goormdotcom.product.repository.mapper;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;

import profect.group1.goormdotcom.product.domain.Product;
import profect.group1.goormdotcom.product.domain.ProductImage;
import profect.group1.goormdotcom.product.domain.ProductStatus;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.repository.entity.ProductImageEntity;
import profect.group1.goormdotcom.product.domain.ProductListItem;

public class ProductMapper {
    
    public static Product toDomain(ProductEntity entity, List<ProductImageEntity> imageEntities) {
        return new Product(
            entity.getId(), 
            entity.getBrandId(), 
            entity.getCategoryId(), 
            entity.getName(), 
            entity.getDescription(), 
            entity.getPrice(),
            entity.getMainImageId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(), 
            entity.getDeletedAt(), 
            imageEntities.stream().map(ProductImageMapper::toDomain).toList()
        );
    }

    public static Product toDomainWithImage(ProductEntity entity, List<ProductImage> images) {
        return new Product(
            entity.getId(), 
            entity.getBrandId(), 
            entity.getCategoryId(), 
            entity.getName(), 
            entity.getDescription(), 
            entity.getPrice(),
            entity.getMainImageId(),
            entity.getCreatedAt(), 
            entity.getUpdatedAt(), 
            entity.getDeletedAt(), 
            images
        );
    }

    public static ProductListItem toProductListItem(ProductEntity entity, String mainImageUrl, ProductStatus status) {
        return new ProductListItem(
            entity.getId(),
            entity.getName(),
            entity.getPrice(),
            mainImageUrl,
            status.getValue()
        );
    }
}
