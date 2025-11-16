package profect.group1.goormdotcom.product.controller.internal.v1.mapper;

import profect.group1.goormdotcom.product.controller.internal.v1.dto.CartProductResponseDto;
import profect.group1.goormdotcom.product.domain.ProductListItem;

public class CartProductDtoMapper {
    public static CartProductResponseDto toProductResponseDto(ProductListItem product) {
        return new CartProductResponseDto(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getMainImageUrl(),
                product.getStatus()
        );
    }
}
