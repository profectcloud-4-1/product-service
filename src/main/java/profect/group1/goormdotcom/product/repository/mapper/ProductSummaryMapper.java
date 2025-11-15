package profect.group1.goormdotcom.product.repository.mapper;

import profect.group1.goormdotcom.product.domain.ProductImage;
import profect.group1.goormdotcom.product.domain.ProductStatus;
import profect.group1.goormdotcom.product.domain.ProductSummary;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.repository.entity.ProductImageEntity;
import profect.group1.goormdotcom.product.repository.entity.ProductSummaryEntity;

public class ProductSummaryMapper {
    public static ProductSummary toDomain(ProductSummaryEntity entity) {
        return new ProductSummary(
                entity.getId(),
                entity.getName(),
                entity.getPrice(),
                entity.getMainImageUrl(),
                entity.getStatus()
        );
    }

    public static ProductSummary toDomainWithProductEntity(ProductEntity entity, ProductImage productImage, ProductStatus status) {
        return new ProductSummary(
                entity.getId(),
                entity.getName(),
                entity.getPrice(),
                productImage.getImageUrl(),
                status.getValue()
        );
    }

    public static ProductSummaryEntity toEntity(ProductSummary productSummary) {
        return new ProductSummaryEntity(
                productSummary.getId(),
                productSummary.getName(),
                productSummary.getPrice(),
                productSummary.getMainImageUrl(),
                productSummary.getStatus()
        );
    }
}
