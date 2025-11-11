package profect.group1.goormdotcom.stock.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import profect.group1.goormdotcom.stock.config.RetryConfig;
import profect.group1.goormdotcom.stock.domain.Stock;
import profect.group1.goormdotcom.stock.domain.exception.InsufficientStockException;
import profect.group1.goormdotcom.stock.repository.StockRepository;
import profect.group1.goormdotcom.stock.repository.entity.StockEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockService 비즈니스 로직 테스트")
public class StockServiceTest {

    @InjectMocks
    private StockService stockService;

    @Mock
    private AdjustStockService adjustStockService;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private RetryConfig retryConfig;

    //stub 헬퍼 메소드
    private StockEntity given_재고가_존재한다(UUID productId, int quantity) {
        StockEntity stockEntity = new StockEntity(UUID.randomUUID(), productId, quantity);
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stockEntity));
        return stockEntity;
    }

    private void given_재고가_존재하지_않는다(UUID productId) {
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.empty());
    }

    private void given_재고가_이미_존재한다(UUID productId) {
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(new StockEntity(UUID.randomUUID(), productId, 100)));
    }

    private void given_재시도_설정(int maxRetries, long backoffMs) {
        when(retryConfig.maxRetries()).thenReturn(maxRetries);
        when(retryConfig.baseOffMs()).thenReturn(backoffMs);
    }

    private void given_재고감소_첫번에_성공한다() {
        doNothing().when(adjustStockService).tryDecreaseStocks(anyMap());
    }

    private void given_재고감소시_락충돌_후_성공한다() {
        doThrow(new ObjectOptimisticLockingFailureException("Locking failure", (Throwable) null))
                .doNothing()
                .when(adjustStockService).tryDecreaseStocks(anyMap());
    }

    private void given_재고감소시_재고부족_예외가_발생한다() {
        doThrow(new InsufficientStockException()).when(adjustStockService).tryDecreaseStocks(anyMap());
    }

    private void given_재고감소시_계속_락충돌이_발생한다() {
        doThrow(new ObjectOptimisticLockingFailureException("Locking failure", (Throwable) null))
                .when(adjustStockService).tryDecreaseStocks(anyMap());
    }

    private void given_재고증가_첫번에_성공한다() {
        doNothing().when(adjustStockService).tryIncreaseStocks(anyMap());
    }

    private void given_재고증가시_락충돌_후_성공한다() {
        doThrow(new ObjectOptimisticLockingFailureException("Locking failure", (Throwable) null))
                .doNothing()
                .when(adjustStockService).tryIncreaseStocks(anyMap());
    }

    private void given_재고증가시_계속_락충돌이_발생한다() {
        doThrow(new ObjectOptimisticLockingFailureException("Locking failure", (Throwable) null))
                .when(adjustStockService).tryIncreaseStocks(anyMap());
    }

    @Nested
    @DisplayName("재고 등록 시나리오")
    class RegisterStockTest {

        @Test
        @DisplayName("성공 - 기존 재고가 없으면 새로운 재고를 등록한다.")
        void registerStock_Success() {
            // given
            UUID productId = UUID.randomUUID();
            given_재고가_존재하지_않는다(productId);

            // when
            stockService.registerStock(productId, 100);

            // then
            verify(stockRepository, times(1)).findByProductId(productId);
            verify(stockRepository, times(1)).save(any(StockEntity.class));
        }

        @Test
        @DisplayName("실패 - 이미 재고가 존재하면 예외가 발생한다.")
        void registerStock_AlreadyExists_ThrowsException() {
            // given
            UUID productId = UUID.randomUUID();
            given_재고가_이미_존재한다(productId);

            // when & then
            assertThatThrownBy(() -> stockService.registerStock(productId, 100))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Stock already exists");
        }
    }

    @Nested
    @DisplayName("재고 조회 시나리오")
    class GetStockTest {
        @Test
        @DisplayName("성공 - 존재하는 재고를 조회한다.")
        void getStock_Success() {
            // given
            UUID productId = UUID.randomUUID();
            given_재고가_존재한다(productId, 100);

            // when
            Stock stock = stockService.getStock(productId);

            // then
            assertThat(stock).isNotNull();
            assertThat(stock.getStockQuantity()).isEqualTo(100);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 재고를 조회하면 예외가 발생한다.")
        void getStock_NotFound_ThrowsException() {
            // given
            UUID productId = UUID.randomUUID();
            given_재고가_존재하지_않는다(productId);

            // when & then
            assertThatThrownBy(() -> stockService.getStock(productId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Product not found");
        }
    }

    @Nested
    @DisplayName("재고 수정 시나리오")
    class UpdateStockTest {
        @Test
        @DisplayName("성공 - 재고 수량을 수정한다.")
        void updateStock_Success() {
            // given
            UUID productId = UUID.randomUUID();
            StockEntity stockEntity = given_재고가_존재한다(productId, 100);
            StockEntity spiedEntity = spy(stockEntity);
            when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(spiedEntity));

            // when
            stockService.updateStock(productId, 50);

            // then
            verify(spiedEntity, times(1)).updateQuantity(50);
            verify(stockRepository, times(1)).save(spiedEntity);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 재고를 수정하면 예외가 발생한다.")
        void updateStock_NotFound_ThrowsException() {
            // given
            UUID productId = UUID.randomUUID();
            given_재고가_존재하지_않는다(productId);

            // when & then
            assertThatThrownBy(() -> stockService.updateStock(productId, 50))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Product not found");
        }
    }

    @Nested
    @DisplayName("재고 삭제 시나리오")
    class DeleteStockTest {
        @Test
        @DisplayName("성공 - 재고를 삭제한다.")
        void deleteStock_Success() {
            // given
            UUID productId = UUID.randomUUID();
            given_재고가_존재한다(productId, 100);

            // when
            stockService.deleteStock(productId);

            // then
            verify(stockRepository, times(1)).deleteByProductId(productId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 재고를 삭제하면 예외가 발생한다.")
        void deleteStock_NotFound_ThrowsException() {
            // given
            UUID productId = UUID.randomUUID();
            given_재고가_존재하지_않는다(productId);

            // when & then
            assertThatThrownBy(() -> stockService.deleteStock(productId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Product not found");
        }
    }

    @Nested
    @DisplayName("재고 차감 시나리오 (낙관적 락 및 재시도 로직)")
    class DecreaseStocksTest {

        private final Map<UUID, Integer> requestMap = Map.of(UUID.randomUUID(), 1);

        @Test
        @DisplayName("성공 - 락 충돌 없이 한번에 재고 차감에 성공한다.")
        void decreaseStocks_Success_OnFirstTry() {
            // given
            given_재고감소_첫번에_성공한다();

            // when
            Boolean result = stockService.decreaseStocks(requestMap);

            // then
            assertThat(result).isTrue();
            verify(adjustStockService, times(1)).tryDecreaseStocks(requestMap);
        }

        @Test
        @DisplayName("성공 - 락 충돌이 1번 발생했지만, 재시도하여 성공한다.")
        void decreaseStocks_Success_AfterOneRetry() {
            // given
            given_재시도_설정(3, 0L);
            given_재고감소시_락충돌_후_성공한다();

            // when
            Boolean result = stockService.decreaseStocks(requestMap);

            // then
            assertThat(result).isTrue();
            verify(adjustStockService, times(2)).tryDecreaseStocks(requestMap);
        }

        @Test
        @DisplayName("실패 - 재고 부족 예외가 발생하면, 재시도 없이 바로 실패한다.")
        void decreaseStocks_InsufficientStock_FailsImmediately() {
            // given
            given_재고감소시_재고부족_예외가_발생한다();

            // when & then
            assertThatThrownBy(() -> stockService.decreaseStocks(requestMap))
                    .isInstanceOf(InsufficientStockException.class);

            verify(adjustStockService, times(1)).tryDecreaseStocks(requestMap);
        }

        @Test
        @DisplayName("실패 - 최대 재시도 횟수를 초과할 때까지 락 충돌이 발생하면, 최종적으로 실패한다.")
        void decreaseStocks_Fails_AfterMaxRetries() {
            // given
            given_재시도_설정(2, 0L);
            given_재고감소시_계속_락충돌이_발생한다();

            // when & then
            assertThatThrownBy(() -> stockService.decreaseStocks(requestMap))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);

            verify(adjustStockService, times(3)).tryDecreaseStocks(requestMap);
        }
    }

    @Nested
    @DisplayName("재고 증가 시나리오 (낙관적 락 및 재시도 로직)")
    class IncreaseStocksTest {

        private final Map<UUID, Integer> requestMap = Map.of(UUID.randomUUID(), 1);

        @Test
        @DisplayName("성공 - 락 충돌 없이 한번에 재고 증가에 성공한다.")
        void increaseStocks_Success_OnFirstTry() {
            // given
            given_재고증가_첫번에_성공한다();

            // when
            Boolean result = stockService.increaseStocks(requestMap);

            // then
            assertThat(result).isTrue();
            verify(adjustStockService, times(1)).tryIncreaseStocks(requestMap);
        }

        @Test
        @DisplayName("성공 - 락 충돌이 1번 발생했지만, 재시도하여 성공한다.")
        void increaseStocks_Success_AfterOneRetry() {
            // given
            given_재시도_설정(3, 0L);
            given_재고증가시_락충돌_후_성공한다();

            // when
            Boolean result = stockService.increaseStocks(requestMap);

            // then
            assertThat(result).isTrue();
            verify(adjustStockService, times(2)).tryIncreaseStocks(requestMap);
        }

        @Test
        @DisplayName("실패 - 최대 재시도 횟수를 초과할 때까지 락 충돌이 발생하면, 최종적으로 실패한다.")
        void increaseStocks_Fails_AfterMaxRetries() {
            // given
            given_재시도_설정(2, 0L);
            given_재고증가시_계속_락충돌이_발생한다();

            // when & then
            assertThatThrownBy(() -> stockService.increaseStocks(requestMap))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);

            verify(adjustStockService, times(3)).tryIncreaseStocks(requestMap);
        }
    }
}
