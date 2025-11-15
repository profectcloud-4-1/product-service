package profect.group1.goormdotcom.product.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.file.FileStorageManager;
import profect.group1.goormdotcom.product.domain.ProductImage;
import profect.group1.goormdotcom.product.domain.ProductStatus;
import profect.group1.goormdotcom.product.domain.ProductSummary;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.StockClient;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.dto.StockResponseDto;
import profect.group1.goormdotcom.product.repository.ProductImageRepository;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.ProductSummaryRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.repository.entity.ProductImageEntity;
import profect.group1.goormdotcom.product.repository.entity.ProductSummaryEntity;
import profect.group1.goormdotcom.product.repository.mapper.ProductImageMapper;
import profect.group1.goormdotcom.product.repository.mapper.ProductSummaryMapper;

import java.util.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductSummaryService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductSummaryRepository productSummaryRepository;

    private final StockClient stockClient;
    private final FileStorageManager fileStorageManager;

    @Value("${aws.cloudfront.domain}")
    private String cloudfrontDomain;
    @Value("${aws.cloudfront.default-image}")
    private String defaultImageObjectKey;


    // 캐싱 없음.
    public List<ProductSummary> getCartProductsV1(
            final List<UUID> productIds
    ) {
        List<ProductSummary> productSummaries = new ArrayList<ProductSummary>();
        ProductSummary notExistsProduct;
        for (UUID productId: productIds) {
            // TODO: 배치 조회로 변경.

            // 1. 상품 존재 여부 파악 -> 없으면 name에 존재하지 않는 상품 표시 보내기
            Optional<ProductEntity> productEntity = productRepository.findByIdIncludingDeleted(productId);
            String baseUrl = cloudfrontDomain.endsWith("/") ? cloudfrontDomain: cloudfrontDomain + '/';
            String imageUrl;
            if (productEntity.isPresent()) {
                ProductEntity entity = productEntity.get();

                // 2. 메인 이미지 존재 여부 파악
                Optional<ProductImageEntity> imageEntity = productImageRepository.findById(entity.getMainImageId());
                ProductImageEntity mainImageEntity = imageEntity.orElse(null);

                // 첫번째 이미지를 카트 화면에서 띄울 메인 이미지로 선택
                String objectKey = "";
                ProductImage mainImage;
                ProductSummary productSummary;

                if (mainImageEntity != null) {
                    try {
                        objectKey = fileStorageManager.getObjectKey(mainImageEntity.getId());
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        objectKey = defaultImageObjectKey;
                    }

                    imageUrl = baseUrl + objectKey;
                    mainImage = ProductImageMapper.toDomainWithImage(mainImageEntity, imageUrl);
                } else {
                    // 이미지를 못 찾은 경우 기본 이미지 제공
                    imageUrl = baseUrl + defaultImageObjectKey;
                    mainImage = new ProductImage(entity.getMainImageId(), entity.getBrandId(), imageUrl);
                }

                // 3. 삭제 여부 확인
                if (entity.getDeletedAt() != null) {
                    productSummary = ProductSummaryMapper.toDomainWithProductEntity(entity, mainImage, ProductStatus.NOT_EXIST);
                } else {
                    // 3. 재고 존재 여부 파악
                    ApiResponse<StockResponseDto> response = stockClient.getStock(productId);
                    if (response.getResult() == null) {

                        // 재고 값을 못받아오면 존재 하지 않는 상품으로 봄
                        productSummary = ProductSummaryMapper.toDomainWithProductEntity(entity, mainImage, ProductStatus.NOT_EXIST);
                    } else if (response.getResult().stockQuantity() <= 0) {
                        // 재고값이 0 이하인 경우 매진
                        productSummary = ProductSummaryMapper.toDomainWithProductEntity(entity, mainImage, ProductStatus.SOLD_OUT);
                    } else {
                        productSummary = ProductSummaryMapper.toDomainWithProductEntity(entity, mainImage, ProductStatus.AVAILABLE);
                    }
                }
                productSummaries.add(productSummary);
            } else {
                imageUrl = baseUrl + defaultImageObjectKey;
                notExistsProduct = new ProductSummary(
                        productId,null, 0, imageUrl, ProductStatus.NOT_EXIST.getValue()
                );
                productSummaries.add(notExistsProduct);
            }
        }

        return productSummaries;
    }

    // 캐싱
    public List<ProductSummary> getCartProducts(
            final List<UUID> productIds
    ) {
        // 1. 캐시 조회
        Map<UUID, ProductSummary> cachedMap = new HashMap<>();
        Iterable<ProductSummaryEntity> entities = productSummaryRepository.findAllById(productIds);
        for (ProductSummaryEntity entity : entities) {
            cachedMap.put(entity.getId(), ProductSummaryMapper.toDomain(entity));
        }
        log.debug("Cached products: {}", cachedMap.entrySet());

        // 2. 배치 조회용 미스 id 리스트 만들기
        List<UUID> missedIds = new ArrayList<>();
        for (UUID id : productIds) {
            if (!cachedMap.containsKey(id)) {
                missedIds.add(id);
            }
        }
        log.debug("Missed productIds: {}", missedIds);
        // 3. 미스 id 배치 조회
        List<ProductSummary> productSummaries = getCartProductsV1(missedIds);

        // 4. 미스난 정보 캐싱
        List<ProductSummaryEntity> productSummaryEntities = productSummaries.stream().map(ProductSummaryMapper::toEntity).toList();
        productSummaryRepository.saveAll(productSummaryEntities);

        Iterable<ProductSummaryEntity> debugEntities = productSummaryRepository.findAllById(missedIds);
        for (ProductSummaryEntity e : debugEntities) {
            log.debug("Saved cache entity: id={}, entity={}", e.getId(), e);
        }

        // 5. 통합본 생성
        productSummaries.addAll(cachedMap.values());

        return productSummaries;
    }
}
