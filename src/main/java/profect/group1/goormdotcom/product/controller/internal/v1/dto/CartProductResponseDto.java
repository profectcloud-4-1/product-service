package profect.group1.goormdotcom.product.controller.internal.v1.dto;

import java.util.UUID;

public record CartProductResponseDto(
    UUID productId,
    String productName,
    int price,
    String imageUrl,
    String status
) {
}
