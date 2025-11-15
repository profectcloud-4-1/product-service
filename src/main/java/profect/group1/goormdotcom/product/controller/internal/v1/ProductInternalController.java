package profect.group1.goormdotcom.product.controller.internal.v1;

import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.apiPayload.code.status.ErrorStatus;
import profect.group1.goormdotcom.product.controller.internal.v1.dto.CartProductResponseDto;
import profect.group1.goormdotcom.product.controller.internal.v1.mapper.CartProductDtoMapper;
import profect.group1.goormdotcom.product.domain.ProductStatus;
import profect.group1.goormdotcom.product.domain.ProductSummary;
import profect.group1.goormdotcom.product.domain.param.ProductSummaryUpdateRequest;
import profect.group1.goormdotcom.product.repository.ProductSummaryRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductSummaryEntity;
import profect.group1.goormdotcom.product.repository.mapper.ProductSummaryMapper;
import profect.group1.goormdotcom.product.service.ProductService;
import profect.group1.goormdotcom.product.service.ProductSummaryService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/product")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInternalController implements ProductInternalApiDocs {
    private final ProductService productService;
    private final ProductSummaryService productSummaryService;

    private final ProductSummaryRepository productSummaryRepository;

    @GetMapping("/cart")
    public ApiResponse<List<CartProductResponseDto>> getCartProducts(
            @RequestParam(value = "product-ids") List<UUID> productIds
    ) {
        List<ProductSummary> products = productSummaryService.getCartProductsV1(productIds);
        return ApiResponse.onSuccess(products.stream().map(CartProductDtoMapper::toProductResponseDto).toList());
    }

    @GetMapping("/save-to-redis")
    public ApiResponse<CartProductResponseDto> saveProductSummary() {
        ProductSummaryEntity productSummaryEntity = ProductSummaryEntity.builder()
                .id(UUID.randomUUID())
                .name("구름캣")
                .price(19900)
                .mainImageUrl("https://d2l2q9yf3ncfgm.cloudfront.net/GoormCat.png")
                .status("AVAILABLE")
                .build();

        productSummaryRepository.save(productSummaryEntity);

        CartProductResponseDto cartProductResponseDto = new CartProductResponseDto(
                productSummaryEntity.getId(),
                productSummaryEntity.getName(),
                productSummaryEntity.getPrice(),
                productSummaryEntity.getMainImageUrl(),
                productSummaryEntity.getStatus()
        );
        return ApiResponse.onSuccess(cartProductResponseDto);
    }

    @GetMapping("/find-in-only-redis")
    public ApiResponse<List<CartProductResponseDto>> findProductSummary(
        @RequestParam(value = "product-ids") List<UUID> productIds
    ) {
        List<ProductSummary> productSummaries = productSummaryService.getCartProducts(productIds);
        return ApiResponse.onSuccess(productSummaries.stream().map(CartProductDtoMapper::toProductResponseDto).toList());
    }

    @PutMapping("/update-in-redis/{productId}")
    public ApiResponse<CartProductResponseDto> updateProductSummary(
            @RequestParam UUID productId
    ) {
        Optional<ProductSummaryEntity> entity = productSummaryRepository.findById(productId);
        if (entity.isEmpty()) {
            return ApiResponse.onFailure(
                    ErrorStatus._NOT_FOUND.getCode(),
                    ErrorStatus._NOT_FOUND.getMessage(),
                    new CartProductResponseDto(productId, null, 0, null, ProductStatus.NOT_EXIST.getValue())
            );
        }
        ProductSummaryEntity productSummaryEntity = entity.get();
        ProductSummary productSummary = ProductSummaryMapper.toDomain(productSummaryEntity);
        ProductSummaryUpdateRequest req = ProductSummaryUpdateRequest.builder()
                .name("메타몽 수정").price(20000).build();

        productSummary.update(req);
        ProductSummaryEntity updatedEntity = ProductSummaryMapper.toEntity(productSummary);
        productSummaryRepository.save(updatedEntity);
        CartProductResponseDto cartProductResponseDto = new CartProductResponseDto(
                updatedEntity.getId(),
                updatedEntity.getName(),
                updatedEntity.getPrice(),
                updatedEntity.getMainImageUrl(),
                updatedEntity.getStatus()
        );
        return ApiResponse.onSuccess(cartProductResponseDto);
    }

    @DeleteMapping("/delete-in-redis/{productId}")
    public ApiResponse<String> deleteProductSummary(
            @RequestParam UUID productId
    ) {
        productSummaryRepository.deleteById(productId);
        return ApiResponse.onSuccess(productId + " 를 삭제했습니다.");
    }
}
