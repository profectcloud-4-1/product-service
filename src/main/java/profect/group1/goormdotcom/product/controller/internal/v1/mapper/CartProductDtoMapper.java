package profect.group1.goormdotcom.product.controller.internal.v1.mapper;

import profect.group1.goormdotcom.product.controller.external.v1.dto.ProductResponseDto;

import profect.group1.goormdotcom.product.controller.internal.v1.dto.CartProductResponseDto;
import profect.group1.goormdotcom.product.domain.ProductImage;
import profect.group1.goormdotcom.product.domain.ProductSummary ;

public class CartProductDtoMapper {
    public static CartProductResponseDto toProductResponseDto(ProductSummary  product) {
        return new CartProductResponseDto(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getMainImage().getImageUrl(),
                product.getStatus().getValue()
        );
    }
}
