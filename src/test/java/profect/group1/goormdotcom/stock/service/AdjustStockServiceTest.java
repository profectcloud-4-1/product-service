package profect.group1.goormdotcom.stock.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import profect.group1.goormdotcom.stock.domain.exception.InsufficientStockException;
import profect.group1.goormdotcom.stock.repository.StockRepository;
import profect.group1.goormdotcom.stock.repository.entity.StockEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdjustStockService 비즈니스 로직 테스트")
public class AdjustStockServiceTest {

    @InjectMocks
    private AdjustStockService adjustStockService;

    @Mock
    private StockRepository stockRepository;

    //stub 헬퍼 메서드, spy로 활용 가능
    private StockEntity given_재고가_존재한다(UUID productId, int initialQuantity) {
        StockEntity stockEntity = new StockEntity(UUID.randomUUID(), productId, initialQuantity);
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(stockEntity));
        return stockEntity;
    }

    private void given_재고가_존재하지_않는다(UUID productId) {
        when(stockRepository.findByProductId(productId)).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("재고 차감 시나리오")
    class TryDecreaseStocksTest {

        @Test
        @DisplayName("성공 - 재고가 충분하면, 엔티티의 decreaseQuantity를 호출하고 저장한다.")
        void tryDecreaseStocks_Success() {
            // given
            UUID productId = UUID.randomUUID();
            int initialQuantity = 10;
            int requestedQuantity = 3;
            Map<UUID, Integer> requestMap = Map.of(productId, requestedQuantity);

            StockEntity stockEntity = given_재고가_존재한다(productId, initialQuantity);

            StockEntity spiedStockEntity = spy(stockEntity);
            when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(spiedStockEntity));

            // when
            adjustStockService.tryDecreaseStocks(requestMap);

            // then
            // 1. Entity의 재고 차감 로직이 정확한 수량으로 호출되었는가?
            verify(spiedStockEntity, times(1)).decreaseQuantity(requestedQuantity);
            // 2. 변경된 Entity가 저장을 위해 Repository에 전달되었는가?
            verify(stockRepository, times(1)).save(spiedStockEntity);
        }

        @Test
        @DisplayName("실패 - 차감할 재고가 존재하지 않으면 예외가 발생한다.")
        void tryDecreaseStocks_StockNotFound_ThrowsException() {
            // given
            UUID productId = UUID.randomUUID();
            Map<UUID, Integer> requestMap = Map.of(productId, 1);
            given_재고가_존재하지_않는다(productId);

            // when & then
            assertThatThrownBy(() -> adjustStockService.tryDecreaseStocks(requestMap))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("실패 - 재고가 요청 수량보다 부족하면 예외가 발생한다.")
        void tryDecreaseStocks_InsufficientStock_ThrowsException() {
            // given
            UUID productId = UUID.randomUUID();
            int initialQuantity = 5;
            int requestedQuantity = 10; // 재고보다 많은 수량 요청
            Map<UUID, Integer> requestMap = Map.of(productId, requestedQuantity);

            StockEntity stockEntity = given_재고가_존재한다(productId, initialQuantity);
            StockEntity spiedStockEntity = spy(stockEntity);
            when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(spiedStockEntity));

            doThrow(new InsufficientStockException()).when(spiedStockEntity).decreaseQuantity(requestedQuantity);

            // when & then
            assertThatThrownBy(() -> adjustStockService.tryDecreaseStocks(requestMap))
                    .isInstanceOf(InsufficientStockException.class);

            // then
            // 재고가 부족하여 실패, save는 호출X
            verify(stockRepository, never()).save(spiedStockEntity);
        }
    }

    @Nested
    @DisplayName("재고 증가 시나리오")
    class TryIncreaseStocksTest {

        @Test
        @DisplayName("성공 - 재고 증가 요청 시, 엔티티의 increaseQuantity를 호출하고 저장한다.")
        void tryIncreaseStocks_Success() {
            // given
            UUID productId = UUID.randomUUID();
            int initialQuantity = 10;
            int requestedQuantity = 5;
            Map<UUID, Integer> requestMap = Map.of(productId, requestedQuantity);

            StockEntity stockEntity = given_재고가_존재한다(productId, initialQuantity);
            StockEntity spiedStockEntity = spy(stockEntity);

            when(stockRepository.findByProductId(productId)).thenReturn(Optional.of(spiedStockEntity));

            // when
            adjustStockService.tryIncreaseStocks(requestMap);

            // then
            verify(spiedStockEntity, times(1)).increaseQuantity(requestedQuantity);
            verify(stockRepository, times(1)).save(spiedStockEntity);
        }
    }
}