package profect.group1.goormdotcom.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;


public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {

    @Query(value = "select * from p_product where id = :id", nativeQuery = true)
    Optional<ProductEntity> findByIdIncludingDeleted(@Param("id") UUID id);

    @Query(value = "select * from p_product where id in (:ids)", nativeQuery = true)
    List<ProductEntity> findAllByIdIncludingDeleted(@Param("ids") Collection<UUID> ids);

    Page<ProductEntity> findByNameContainingIgnoreCase(String keyword, Pageable pageable);
}
