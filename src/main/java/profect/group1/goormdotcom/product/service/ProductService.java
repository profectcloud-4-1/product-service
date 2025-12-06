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
import profect.group1.goormdotcom.product.domain.ProductStatus;
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
import profect.group1.goormdotcom.product.service.utils.ImageUrlGenerator;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    private final StockClient stockClient;
    private final FileStorageManager fileStorageManager;

    private final ImageUrlGenerator imageUrlGenerator;

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

        ProductEntity productEntity = new ProductEntity(
            productId, 
            brandId, 
            categoryId, 
            productName, 
            price,
            mainImageId,
            description
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
        // for (UUID imageId: imageIds) {
        //    fileStorageManager.confirmUpload(imageId);
        // }
        
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

        ProductEntity newProductEntity = new ProductEntity(
            productId, productEntity.getBrandId(), categoryId, productName, price, mainImageId, description
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

    public Product getProduct(
        final UUID productId
    ) {
        ProductEntity productEntity = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        List<ProductImageEntity> imageEntities = productImageRepository.findByProductId(productId);

        // TODO: presigned server에서 여러 이미지의 object key를 한번에 조회할 수 있는 api가 필요할 듯
        List<ProductImage> images = new ArrayList<>();
        for (ProductImageEntity imageEntity: imageEntities) {
            images.add(ProductImageMapper.toDomainWithImage(
                    imageEntity, imageUrlGenerator.generateProductImageUrl(imageEntity.getId())));
        }

        return ProductMapper.toDomainWithImage(productEntity, images);
    }

    public void deleteProductImage(final UUID imageId) {
        productImageRepository.deleteById(imageId);
    }

    public void deleteProductImages(final List<UUID> imageIds) {
        productImageRepository.deleteAllById(imageIds);
    }
}
