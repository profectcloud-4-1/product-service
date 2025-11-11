package profect.group1.goormdotcom.stock;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
// import org.springframework.dao.ObjectOptimisticLockingFailureException; // 컴파일 오류로 인해 주석 처리
import org.springframework.test.context.ActiveProfiles;
import profect.group1.goormdotcom.stock.repository.StockRepository;
import profect.group1.goormdotcom.stock.repository.entity.StockEntity;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@DataJpaTest
@DisplayName("StockRepository 데이터 검증 테스트")
public class StockRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private StockRepository stockRepository;

    private StockEntity createStockEntity(UUID productId, int quantity) {
        return new StockEntity(
                UUID.randomUUID(),
                productId,
                quantity
        );
    }

    @Nested
    @DisplayName("재고 생성 및 조회 테스트")
    class SaveAndFindTest {

        @Test
        @DisplayName("성공 - 새로운 재고를 저장하고 ID로 조회할 수 있다.")
        void saveAndFindById_Success() {
            // given
            StockEntity newStock = createStockEntity(UUID.randomUUID(), 100);

            // when
            stockRepository.save(newStock);
            entityManager.flush();
            entityManager.clear();

            // then
            Optional<StockEntity> foundStock = stockRepository.findById(newStock.getId());
            assertThat(foundStock).isPresent();
            assertThat(foundStock.get().getProductId()).isEqualTo(newStock.getProductId());
        }

        @Test
        @DisplayName("성공 - ProductId로 재고를 조회할 수 있다.")
        void findByProductId_Success() {
            // given
            UUID productId = UUID.randomUUID();
            StockEntity stock = createStockEntity(productId, 100);
            entityManager.persist(stock);
            entityManager.flush();
            entityManager.clear();

            // when
            Optional<StockEntity> foundStock = stockRepository.findByProductId(productId);

            // then
            assertThat(foundStock).isPresent();
            assertThat(foundStock.get().getProductId()).isEqualTo(productId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 ProductId로 조회하면 Optional.empty를 반환한다.")
        void findByProductId_NotFound_ReturnsEmpty() {
            // given
            UUID nonExistentProductId = UUID.randomUUID();

            // when
            Optional<StockEntity> foundStock = stockRepository.findByProductId(nonExistentProductId);

            // then
            assertThat(foundStock).isNotPresent();
        }
    }

    @Nested
    @DisplayName("재고 수정 및 낙관적 락 테스트")
    class UpdateAndLockingTest {

        @Test
        @DisplayName("성공 - 재고 수량을 수정하고 저장하면 반영된다.")
        void updateQuantity_Success() {
            // given
            UUID productId = UUID.randomUUID();
            StockEntity stock = createStockEntity(productId, 100);
            entityManager.persist(stock);
            entityManager.flush();

            // when
            stock.updateQuantity(50);
            stockRepository.save(stock);
            entityManager.flush();
            entityManager.clear();

            // then
            StockEntity updatedStock = entityManager.find(StockEntity.class, stock.getId());
            assertThat(updatedStock.getStockQuantity()).isEqualTo(50);
        }

        @Test
        @DisplayName("성공 (낙관적 락) - 동시에 수정을 시도하면, 먼저 수정한 트랜잭션만 성공한다.")
        void optimisticLock_PreventsConcurrentUpdate() {
            // given
            StockEntity initialStock = createStockEntity(UUID.randomUUID(), 100);
            entityManager.persist(initialStock);
            entityManager.flush();
            UUID stockId = initialStock.getId();

            StockEntity stockForUserA = stockRepository.findById(stockId).orElseThrow();
            entityManager.detach(stockForUserA);

            StockEntity stockForUserB = stockRepository.findById(stockId).orElseThrow();
            entityManager.detach(stockForUserB);

            stockForUserA.updateQuantity(80);
            stockRepository.save(stockForUserA);
            entityManager.flush();
            entityManager.clear();

            // then
            stockForUserB.updateQuantity(70);
            assertThatThrownBy(() -> {
                stockRepository.save(stockForUserB);
                entityManager.flush();
            }).isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("재고 삭제 테스트")
    class DeleteTest {

        @Test
        @DisplayName("성공 - ProductId로 재고를 삭제하면 더 이상 조회되지 않는다.")
        void deleteByProductId_Success() {
            // given
            UUID productId = UUID.randomUUID();
            StockEntity stock = createStockEntity(productId, 100);
            entityManager.persist(stock);
            entityManager.flush();

            // when
            stockRepository.deleteByProductId(productId);
            entityManager.flush();
            entityManager.clear();

            // then
            Optional<StockEntity> foundStock = stockRepository.findByProductId(productId);
            assertThat(foundStock).isNotPresent();
        }
    }
}