package profect.group1.goormdotcom.product.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.file.FileStorageManager;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.domain.ProductStatus;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.StockClient;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.dto.StockResponseDto;
import profect.group1.goormdotcom.product.repository.ProductImageRepository;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.repository.mapper.ProductMapper;
import profect.group1.goormdotcom.product.service.utils.ImageUrlGenerator;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductListItemCacheService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    private final StockClient stockClient;
    private final FileStorageManager fileStorageManager;

    private final ImageUrlGenerator imageUrlGenerator;

    @Cacheable(cacheNames = "productSummary", key="#productId")
    public ProductListItem getCartProductListItem(UUID productId) { // 캐시 조회 로직
        return getCartProductListItemFromOrigin(productId);
    }

    public ProductListItem getCartProductListItemFromOrigin(UUID productId) { // DB 조회 로직
        ProductListItem productListItem;

        // 1. 상품 존재 여부 파악 -> 없으면 name에 존재하지 않는 상품 표시 보내기
        Optional<ProductEntity> productEntity = productRepository.findByIdIncludingDeleted(productId);

        String imageUrl;
        if (productEntity.isPresent()) {
            ProductEntity entity = productEntity.get();

            // 첫번째 이미지를 카트 화면에서 띄울 메인 이미지로 선택
            imageUrl = imageUrlGenerator.generateProductImageUrl(entity.getMainImageId());

            // 2. 삭제 여부 확인
            if (entity.getDeletedAt() != null) {
                productListItem = ProductMapper.toProductListItem(entity, imageUrl, ProductStatus.NOT_EXIST);
            } else {
                // 3. 재고 존재 여부 파악
                ApiResponse<StockResponseDto> response = stockClient.getStock(productId);
                if (response.getResult() == null) {

                    // 재고 값을 못받아오면 존재 하지 않는 상품으로 봄
                    productListItem = ProductMapper.toProductListItem(entity, imageUrl, ProductStatus.NOT_EXIST);
                } else if (response.getResult().stockQuantity() <= 0) {
                    // 재고값이 0 이하인 경우 매진
                    productListItem = ProductMapper.toProductListItem(entity, imageUrl, ProductStatus.SOLD_OUT);
                } else {
                    productListItem = ProductMapper.toProductListItem(entity, imageUrl, ProductStatus.AVAILABLE);
                }
            }

        } else {
            imageUrl = imageUrlGenerator.generateDefaultImageUrl();
            productListItem = new ProductListItem(
                    productId,null, 0, imageUrl, ProductStatus.NOT_EXIST.getValue()
            );
        }
        return productListItem;
    }
}
