package profect.group1.goormdotcom.product.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import profect.group1.goormdotcom.product.domain.ProductListItem;
import profect.group1.goormdotcom.product.domain.ProductStatus;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;
import profect.group1.goormdotcom.product.repository.mapper.ProductMapper;
import profect.group1.goormdotcom.product.service.utils.ImageUrlGenerator;

import java.util.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductListItemService {

    private final ProductRepository productRepository;
    private final ImageUrlGenerator imageUrlGenerator;
    private final ProductListItemCacheService productListItemCacheService;
    private final ProductListItemOriginService productListItemOriginService;

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

        // TODO: 재고 조회

        return resultPage.getContent().stream().map((entity) -> ProductMapper.toProductListItem(
                entity, imageUrlGenerator.generateProductImageUrl(entity.getMainImageId()), ProductStatus.AVAILABLE)).toList();
    }

    public List<ProductListItem> getCartProducts(final List<UUID> productIds) {
        return productListItemCacheService.getCartProductListItemsBulk(productIds);
    }

    public List<ProductListItem> getCartProductsFromOrigin(final List<UUID> productIds) {
        return productListItemOriginService.getCartProductListItemsBulkFromOrigin(productIds);
    }
}
