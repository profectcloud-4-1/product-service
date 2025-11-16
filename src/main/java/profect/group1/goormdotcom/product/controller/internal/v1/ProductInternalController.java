package profect.group1.goormdotcom.product.controller.internal.v1;

import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.product.controller.internal.v1.dto.CartProductResponseDto;
import profect.group1.goormdotcom.product.controller.internal.v1.mapper.CartProductDtoMapper;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.service.ProductService;
import profect.group1.goormdotcom.product.service.ProductListItemService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/product")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInternalController implements ProductInternalApiDocs {
    private final ProductService productService;
    private final ProductListItemService productSummaryService;

    @GetMapping("/cart")
    public ApiResponse<List<CartProductResponseDto>> getCartProducts(
            @RequestParam(value = "product-ids") List<UUID> productIds
    ) {

        // 다른 브랜치에 실험용으로 빼기
        List<ProductListItem> products;
        products = productSummaryService.getCartProducts(productIds);

        return ApiResponse.onSuccess(products.stream().map(CartProductDtoMapper::toProductResponseDto).toList());
    }
}
