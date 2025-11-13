package profect.group1.goormdotcom.product.controller.internal.v1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.product.controller.internal.v1.dto.CartProductResponseDto;
import profect.group1.goormdotcom.product.controller.internal.v1.mapper.CartProductDtoMapper;
import profect.group1.goormdotcom.product.domain.ProductSummary;
import profect.group1.goormdotcom.product.service.ProductService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/product")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInternalController implements ProductInternalApiDocs {
    private final ProductService productService;

    @GetMapping("/cart")
    public ApiResponse<List<CartProductResponseDto>> getCartProducts(
            @RequestParam(value = "product-id") List<UUID> productIds
    ) {
        List<ProductSummary> products = productService.getCartProducts(productIds);
        return ApiResponse.onSuccess(products.stream().map(CartProductDtoMapper::toProductResponseDto).toList());
    }

}
