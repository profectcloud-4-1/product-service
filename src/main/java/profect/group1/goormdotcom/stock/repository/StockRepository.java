package profect.group1.goormdotcom.stock.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import profect.group1.goormdotcom.stock.repository.entity.StockEntity;

public interface StockRepository extends JpaRepository<StockEntity, UUID>{
    
    public Optional<StockEntity> findByProductId(UUID productId);

    public List<StockEntity> findAllByProductIdIn(Collection<UUID> productIds);
    
    public void deleteByProductId(UUID productId);
}
