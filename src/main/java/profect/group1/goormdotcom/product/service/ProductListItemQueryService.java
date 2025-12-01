package profect.group1.goormdotcom.product.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.apiPayload.code.status.ErrorStatus;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.domain.ProductStatus;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.StockClient;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.dto.StockResponseDto;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.repository.mapper.ProductMapper;
import profect.group1.goormdotcom.product.service.utils.ImageUrlGenerator;
import profect.group1.goormdotcom.stock.service.StockService;

import java.util.*;

@RequiredArgsConstructor
@Service
@Slf4j
public class ProductListItemQueryService {

    private final ProductRepository productRepository;

    private final StockClient stockClient;

    private final ImageUrlGenerator imageUrlGenerator;

    private final StockService stockService;

    private final ProductQueryService productQueryService;

    // 단건 조회 로직
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
                long startTime = System.currentTimeMillis();
                log.info("call stock start {}", productId);
                ApiResponse<StockResponseDto> response;
                try {
                    response = stockClient.getStock(productId);
                } catch (Exception e) {
                    log.error("call stock error {}", productId, e);
                    response = ApiResponse.onFailure(ErrorStatus._CONFLICT.getCode(), ErrorStatus._CONFLICT.getMessage(), null);
                }
                long endTime = System.currentTimeMillis();
                long timeElapsed = endTime - startTime;
                log.info("call stock end {} duration={}ms", productId, (double)timeElapsed);

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

    // 다건 조회 로직
    public List<ProductListItem> getCartProductListItemsBulkFromOrigin(List<UUID> productIds) { // DB 조회 로직
        List<ProductListItem> productListItemList = new ArrayList<>();

        // productId를 키로 하는 hashmap 생성.
        Map<UUID, ProductEntity> productEntityMap = new HashMap<>();
        Map<UUID, ProductListItem> productListItemMap = new HashMap<>();

        // DB 조회
        List<ProductEntity> productEntities = productQueryService.getProductEntities(productIds);
        for (ProductEntity productEntity : productEntities) productEntityMap.put(productEntity.getId(), productEntity);

        // 재고 조회
        ApiResponse<List<StockResponseDto>> response = stockClient.getStocksBulk(productIds);

        if (response.getResult() == null) {
            for (UUID productId : productIds) {
                // 엔터티
                ProductEntity productEntity = productEntityMap.get(productId);
                // 이미지
                String imageUrl = imageUrlGenerator.generateProductImageUrl(productEntity.getMainImageId());
                productListItemMap.put(productId, ProductMapper.toProductListItem(productEntity, imageUrl, ProductStatus.NOT_EXIST));
            }
        } else {
            List<StockResponseDto> stockResponseDtos = response.getResult();
            for (StockResponseDto stockResponseDto : stockResponseDtos) {
                // 엔터티
                ProductEntity productEntity = productEntityMap.get(stockResponseDto.productId());
                // 이미지
                String imageUrl = imageUrlGenerator.generateProductImageUrl(productEntity.getMainImageId());

                if (productEntity.getDeletedAt() == null) {
                    if (stockResponseDto.stockQuantity() > 0) {
                        productListItemMap.put(stockResponseDto.productId(), ProductMapper.toProductListItem(productEntity, imageUrl, ProductStatus.AVAILABLE));
                    } else {
                        productListItemMap.put(stockResponseDto.productId(), ProductMapper.toProductListItem(productEntity, imageUrl, ProductStatus.SOLD_OUT));
                    }
                } else {
                    ///  상품 삭제
                    productListItemMap.put(stockResponseDto.productId(), ProductMapper.toProductListItem(productEntity, imageUrl, ProductStatus.NOT_EXIST));
                }

            }
        }

        // 존재하지 않는 아이템 모두 추가.
        for (UUID productId : productIds) {
            if (!productEntityMap.containsKey(productId)) {
                String imageUrl = imageUrlGenerator.generateDefaultImageUrl();
                productListItemMap.put(productId, new ProductListItem(productId,null, 0, imageUrl, ProductStatus.NOT_EXIST.getValue()));
            }
        }

        // 요청 순서대로 리스트 구성.
        for (UUID productId : productIds) productListItemList.add(productListItemMap.get(productId));

        return productListItemList;
    }
}
