package profect.group1.goormdotcom.product.repository;

import org.springframework.data.repository.CrudRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductSummaryEntity;

import java.util.UUID;

public interface ProductSummaryRepository extends CrudRepository<ProductSummaryEntity, UUID> {
}
