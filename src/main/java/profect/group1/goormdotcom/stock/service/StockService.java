package profect.group1.goormdotcom.stock.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.StaleObjectStateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import profect.group1.goormdotcom.stock.config.RetryConfig;
import profect.group1.goormdotcom.stock.domain.Stock;
import profect.group1.goormdotcom.stock.domain.exception.InsufficientStockException;
import profect.group1.goormdotcom.stock.repository.StockRepository;
import profect.group1.goormdotcom.stock.repository.entity.StockEntity;
import profect.group1.goormdotcom.stock.repository.mapper.StockMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
    
    private final AdjustStockService adjustStockService;
    private final StockRepository stockRepository;
    private final RetryConfig retryConfig;

    private StockEntity getStockEntity(UUID productId) {
        Optional<StockEntity> stockEntity = stockRepository.findByProductId(productId);
        
        if (stockEntity.isEmpty()) {
			throw new IllegalArgumentException("Product not found");
		}
        return stockEntity.get(); 
    }

    @Transactional
    public Stock registerStock(UUID productId, int stockQuantity) {
        stockRepository.findByProductId(productId)
            .ifPresent(stock -> { throw new IllegalArgumentException("Stock already exists");});

        UUID id = UUID.randomUUID();
        StockEntity entity = new StockEntity(id, productId, stockQuantity);
        stockRepository.save(entity);
        return StockMapper.toDomain(entity);
    }
    
    @Transactional
    public Stock updateStock(UUID productId, int stockQuantity) {
        StockEntity entity = getStockEntity(productId);
        entity.updateQuantity(stockQuantity);
        stockRepository.save(entity);
        return StockMapper.toDomain(entity);
    }

    @Transactional(readOnly = true)
    public Stock getStock(UUID productId) {
        StockEntity entity = getStockEntity(productId);
        return StockMapper.toDomain(entity);
    }

    @Transactional
    public Stock deleteStock(UUID productId) {
        StockEntity entity = getStockEntity(productId);
        stockRepository.deleteByProductId(productId);

        Stock stock = StockMapper.toDomain(entity);
        return stock;
    }

    public Boolean decreaseStocks(Map<UUID, Integer> requestedQuantityMap) {
        int retryCount = 0;

        while (true) {
            try {
                // 여러 건의 상품 재고 차감 트랜잭션
                adjustStockService.tryDecreaseStocks(requestedQuantityMap);
                break;
            } catch (InsufficientStockException e) {
                throw e;
            } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
                retryCount += 1;

                if (retryCount > retryConfig.maxRetries()) {
                    log.info("재고 차감 실패");
                    throw e;
                }

                try {
                    Thread.sleep(retryConfig.baseOffMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return true;
    }

    public Boolean increaseStocks(Map<UUID, Integer> requestedQuantityMap) {
        int retryCount = 0;

        while (true) {
            try {
                // 여러 건의 상품에 대해서 재고 증가 시도
                adjustStockService.tryIncreaseStocks(requestedQuantityMap);
                break;
            } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
                retryCount += 1;

                if (retryCount > retryConfig.maxRetries()) {
                    log.info("재고 증가 실패");
                    // return AdjustStockStatus.FAILED;
                    throw e;
                }

                try {
                    Thread.sleep(retryConfig.baseOffMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }
}
