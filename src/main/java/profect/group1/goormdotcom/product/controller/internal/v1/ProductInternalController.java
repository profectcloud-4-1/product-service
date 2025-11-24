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
    private final ProductListItemService productListItemService;

    @GetMapping("/cart")
    public ApiResponse<List<CartProductResponseDto>> getCartProducts(
            @RequestParam(value = "product-ids") List<UUID> productIds
    ) {
        List<ProductListItem> products;
        products = productListItemService.getCartProducts(productIds);

        return ApiResponse.onSuccess(products.stream().map(CartProductDtoMapper::toProductResponseDto).toList());
    }

    @GetMapping("/cart/nolock")
    public ApiResponse<List<CartProductResponseDto>> getCartProductsWithoutLock(
            @RequestParam(value = "product-ids") List<UUID> productIds
    ) {
        List<ProductListItem> products;
        products = productListItemService.getCartProductsWithoutLock(productIds);

        return ApiResponse.onSuccess(products.stream().map(CartProductDtoMapper::toProductResponseDto).toList());
    }


    @GetMapping("/cart/nocache")
    public ApiResponse<List<CartProductResponseDto>> getCartProductsFromOrigin(
            @RequestParam(value = "product-ids") List<UUID> productIds
    ) {
        List<ProductListItem> products;
        products = productListItemService.getCartProductsFromOrigin(productIds);

        return ApiResponse.onSuccess(products.stream().map(CartProductDtoMapper::toProductResponseDto).toList());
    }
}
