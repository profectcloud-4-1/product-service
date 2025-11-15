package profect.group1.goormdotcom.product.service;

import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.file.FileStorageManager;
import profect.group1.goormdotcom.product.domain.Product;
import profect.group1.goormdotcom.product.domain.ProductImage;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.StockClient;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.dto.StockRequestDto;
import profect.group1.goormdotcom.product.infrastructure.client.StockService.dto.StockResponseDto;
import profect.group1.goormdotcom.product.repository.ProductImageRepository;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.repository.entity.ProductImageEntity;
import profect.group1.goormdotcom.product.repository.mapper.ProductMapper;
import profect.group1.goormdotcom.product.repository.mapper.ProductImageMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    private final StockClient stockClient;
    private final FileStorageManager fileStorageManager;

    @Value("${aws.cloudfront.domain}")
    private String cloudfrontDomain;
    @Value("${aws.cloudfront.default-image}")
    private String defaultImageObjectKey;

    public UUID createProduct(
        final UUID brandId,
        final UUID categoryId,
        final String productName,
        final int price,
        final int stockQuantity,
        final UUID mainImageId,
        final String description,
        final List<UUID> imageIds
    ) {
        final UUID productId = UUID.randomUUID();

        String mainImageUri = fileStorageManager.getObjectKey(mainImageId);
        
        ProductEntity productEntity = new ProductEntity(
            productId, 
            brandId, 
            categoryId, 
            productName, 
            price,
            mainImageId,
            description,
            mainImageUri
        );
        
        // 재고 등록 요청
        StockRequestDto stockRequestDto = new StockRequestDto(productId, stockQuantity);
        ApiResponse<StockResponseDto> response = stockClient.registerStock(stockRequestDto);
        StockResponseDto stockResponseDto = response.getResult();
        if (stockResponseDto == null) {
            throw new IllegalStateException("Failed to register stock");
        }

        // 상품 이미지 메타 정보 저장 (실제 이미지는 presignedURL을 통해 S3로 직접 업로드 되었음.)
        List<ProductImageEntity> productImageEntities = imageIds.stream().map((imageId) -> new ProductImageEntity(imageId, productId)).toList();
        productImageRepository.saveAll(productImageEntities);

        // Image confirm 요청
        for (UUID imageId: imageIds) {
            fileStorageManager.confirmUpload(imageId);
        }
        
        productRepository.save(productEntity);
            
        return productId;
    }

    public Product updateProduct(
        final UUID productId,
        final UUID bradnId,
        final UUID categoryId,
        final String productName,
        final int price,
        final String description,
        final UUID mainImageId,
        final List<UUID> imageIds
    ) {
        ProductEntity productEntity = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Prdocut not found"));

        // if (productEntity.getBrandId() != bradnId) {
        //     throw new IllegalStateException("Product is not owned by your brand.");
        // }

        String mainImageUri = fileStorageManager.getObjectKey(mainImageId);
        ProductEntity newProductEntity = new ProductEntity(
            productId, productEntity.getBrandId(), categoryId, productName, price, mainImageId, description, mainImageUri
        );

        // 새롭게 업로드 된 이미지 저장. (삭제된 이미지는 프론트엔드에서 delete요청 보내서 soft delete 처리, 새롭게 업로드 된 메타정보 저장.)
        List<ProductImageEntity> productImageEntities = imageIds.stream().map((imageId) -> new ProductImageEntity(imageId, productId)).toList();
        productImageRepository.saveAll(productImageEntities);

        // Image confirm 요청
        for (UUID imageId: imageIds) {
            fileStorageManager.confirmUpload(imageId);
        }
        
        productRepository.save(newProductEntity);

        return ProductMapper.toDomain(newProductEntity, productImageEntities);
    }

    public void deleteProduct(
        final UUID productId,
        final UUID brandId
    ) {
        productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Prdocut not found"));

        // if (productEntity.getBrandId() != brandId) {
        //     throw new IllegalStateException("Product is not owned by your brand.");
        // }
        productRepository.deleteById(productId);
        List<ProductImageEntity> imageEntities = productImageRepository.findByProductId(productId);
        List<UUID> imageIds = imageEntities.stream().map(ProductImageEntity::getId).toList();
        deleteProductImages(imageIds);
    }

    public void deleteProducts(
        final List<UUID> productIds,
        final UUID brandId
    ) {

        // for (UUID productId: productIds) {
        //     ProductEntity productEntity = productRepository.findById(productId)
        //         .orElseThrow(() -> new IllegalArgumentException("Prdocut not found"));

        //     if (productEntity.getBrandId() != brandId) {
        //         throw new IllegalStateException("Product is not owned by your brand.");
        //     }
        // }
        productRepository.deleteAllById(productIds);
        
    }

    public List<ProductListItem> getProducts(
        final int page,
        final int size,
        final String sort,
        final String order,
        final String keyword
    ) {
        // 정렬
        Sort.Direction direction =
                (order != null && order.equalsIgnoreCase("asc"))
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;
        int zeroBasedPage = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(zeroBasedPage, size, Sort.by(direction, sort));

        Page<ProductEntity> resultPage;

        // keyword가 없는 경우 전체 조회
        if (keyword == null || keyword.isBlank()) {
            resultPage = productRepository.findAll(pageable);
        } else {
            // keyword가 있는 경우 LIKE 검색
            resultPage = productRepository.findByNameContainingIgnoreCase(keyword, pageable);
        }

        return resultPage.getContent().stream().map((entity) -> ProductMapper.toProductListItem(entity, cloudfrontDomain)).toList();
    }

    public Product getProduct(
        final UUID productId
    ) {
        ProductEntity productEntity = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        Map<ProductImageEntity, String> urlMapping = new HashMap<ProductImageEntity, String>();
        List<ProductImageEntity> imageEntities = productImageRepository.findByProductId(productId);
        String baseUrl = cloudfrontDomain.endsWith("/") ? cloudfrontDomain: cloudfrontDomain + '/';
        // TODO: presigned server에서 여러 이미지의 object key를 한번에 조회할 수 있는 api가 필요할 듯
        for (ProductImageEntity imageEntity: imageEntities) {
            String objectKey;
            try {
                objectKey = fileStorageManager.getObjectKey(imageEntity.getId());
            } catch (IllegalArgumentException | IllegalStateException e) {
                // TODO: 이미지가 없을 경우 어떻게 처리?
                // 기본 이미지가 있어야 할 것 같다. (goorm 이미지?)
                objectKey = defaultImageObjectKey;
            }

            urlMapping.put(imageEntity,  baseUrl + objectKey);
        }
        
        List<ProductImage> images = urlMapping.keySet().stream()
            .map((imageEntity) -> ProductImageMapper.toDomainWithImage(imageEntity, urlMapping.get(imageEntity))).toList();

        return ProductMapper.toDomainWithImage(productEntity, images);
    }

    public void deleteProductImage(final UUID imageId) {
        productImageRepository.deleteById(imageId);
    }

    public void deleteProductImages(final List<UUID> imageIds) {
        productImageRepository.deleteAllById(imageIds);
    }
}
