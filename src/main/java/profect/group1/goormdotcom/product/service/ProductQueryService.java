package profect.group1.goormdotcom.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductQueryService {
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ProductEntity> getProductEntities(List<UUID> productIds) {
        return productRepository.findAllByIdIncludingDeleted(productIds);
    }

}
