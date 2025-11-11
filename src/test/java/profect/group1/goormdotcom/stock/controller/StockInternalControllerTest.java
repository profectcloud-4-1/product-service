package profect.group1.goormdotcom.stock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import profect.group1.goormdotcom.common.apiPayload.code.status.ErrorStatus;
import profect.group1.goormdotcom.common.apiPayload.exceptions.ExceptionAdvice;
import profect.group1.goormdotcom.stock.controller.internal.v1.StockInternalController;
import profect.group1.goormdotcom.stock.controller.internal.v1.dto.ProductStockAdjustmentRequestDto;
import profect.group1.goormdotcom.stock.controller.internal.v1.dto.StockAdjustmentRequestDto;
import profect.group1.goormdotcom.stock.domain.exception.InsufficientStockException;
import profect.group1.goormdotcom.stock.service.StockService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockInternalController 단위 테스트")
public class StockInternalControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private StockInternalController stockInternalController;

    @Mock
    private StockService stockService;

    private final String BASE_URL = "/internal/v1/stock";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(stockInternalController)
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    private StockAdjustmentRequestDto createRequestDto() {
        UUID productId = UUID.randomUUID();
        ProductStockAdjustmentRequestDto productDto = new ProductStockAdjustmentRequestDto(productId, 1);
        return new StockAdjustmentRequestDto(List.of(productDto));
    }

    @Nested
    @DisplayName("재고 차감 (POST /decrease)")
    class DecreaseStocksTest {

        @Test
        @DisplayName("성공 - 재고 차감에 성공하면, status:true와 200 OK를 반환한다.")
        void decreaseStocks_Success() throws Exception {
            // given
            StockAdjustmentRequestDto request = createRequestDto();
            given(stockService.decreaseStocks(anyMap())).willReturn(true);

            // when & then
            mockMvc.perform(post(BASE_URL + "/decrease")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result.status").value(true))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 - 서비스가 false를 반환하면, status:false와 409 Conflict를 반환한다.")
        void decreaseStocks_ServiceReturnsFalse_Fails() throws Exception {
            // given
            StockAdjustmentRequestDto request = createRequestDto();
            given(stockService.decreaseStocks(anyMap())).willReturn(false);

            // when & then
            mockMvc.perform(post(BASE_URL + "/decrease")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorStatus._CONFLICT.getCode()))
                    .andExpect(jsonPath("$.result.status").value(false))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (재고 부족) - 재고 부족 예외 발생 시, status:false와 재고 부족 코드를 반환한다.")
        void decreaseStocks_InsufficientStock_Fails() throws Exception {
            // given
            StockAdjustmentRequestDto request = createRequestDto();
            given(stockService.decreaseStocks(anyMap())).willThrow(new InsufficientStockException());

            // when & then
            mockMvc.perform(post(BASE_URL + "/decrease")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorStatus._INSUFFICIENT_STOCK_QUANTITY.getCode()))
                    .andExpect(jsonPath("$.result.status").value(false))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (낙관적 락) - 락 충돌 예외 발생 시, status:false와 재고 조정 실패 코드를 반환한다.")
        void decreaseStocks_LockingFailure_Fails() throws Exception {
            // given
            StockAdjustmentRequestDto request = createRequestDto();
            given(stockService.decreaseStocks(anyMap())).willThrow(new ObjectOptimisticLockingFailureException("Locking failure", (Throwable) null));

            // when & then
            mockMvc.perform(post(BASE_URL + "/decrease")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorStatus._ADJUST_STOCK_FAILED.getCode()))
                    .andExpect(jsonPath("$.result.status").value(false))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("재고 증가 (POST /increase)")
    class IncreaseStocksTest {

        @Test
        @DisplayName("성공 - 재고 증가에 성공하면, status:true와 200 OK를 반환한다.")
        void increaseStocks_Success() throws Exception {
            // given
            StockAdjustmentRequestDto request = createRequestDto();
            given(stockService.increaseStocks(anyMap())).willReturn(true);

            // when & then
            mockMvc.perform(post(BASE_URL + "/increase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result.status").value(true))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (서비스 로직) - 서비스가 false를 반환하면, status:false와 409 Conflict를 반환한다.")
        void increaseStocks_ServiceReturnsFalse_Fails() throws Exception {
            // given
            StockAdjustmentRequestDto request = createRequestDto();
            given(stockService.increaseStocks(anyMap())).willReturn(false);

            // when & then
            mockMvc.perform(post(BASE_URL + "/increase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorStatus._CONFLICT.getCode()))
                    .andExpect(jsonPath("$.result.status").value(false))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (낙관적 락) - 락 충돌 예외 발생 시, status:false와 재고 조정 실패 코드를 반환한다.")
        void increaseStocks_LockingFailure_Fails() throws Exception {
            // given
            StockAdjustmentRequestDto request = createRequestDto();
            given(stockService.increaseStocks(anyMap())).willThrow(new ObjectOptimisticLockingFailureException("Locking failure", (Throwable) null));

            // when & then
            mockMvc.perform(post(BASE_URL + "/increase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ErrorStatus._ADJUST_STOCK_FAILED.getCode()))
                    .andExpect(jsonPath("$.result.status").value(false))
                    .andDo(print());
        }
    }
}