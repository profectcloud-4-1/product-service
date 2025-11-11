package profect.group1.goormdotcom.stock.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import profect.group1.goormdotcom.common.apiPayload.code.status.ErrorStatus;
import profect.group1.goormdotcom.common.apiPayload.exceptions.ExceptionAdvice;
import profect.group1.goormdotcom.common.apiPayload.exceptions.GeneralException;
import profect.group1.goormdotcom.stock.controller.external.v1.StockExternalController;
import profect.group1.goormdotcom.stock.controller.external.v1.dto.StockRequestDto;
import profect.group1.goormdotcom.stock.controller.external.v1.dto.StockResponseDto;
import profect.group1.goormdotcom.stock.controller.mapper.StockDtoMapper;
import profect.group1.goormdotcom.stock.domain.Stock;
import profect.group1.goormdotcom.stock.service.StockService;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockExternalController 단위 테스트")
public class StockExternalControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private StockExternalController stockExternalController;

    @Mock
    private StockService stockService;

    private final String BASE_URL = "/api/v1/stock";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(stockExternalController)
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    @Nested
    @DisplayName("재고 등록 (POST /api/v1/stock)")
    class RegisterStockTest {

        @Test
        @DisplayName("성공 - 올바른 요청 시, 등록된 재고 정보와 200 OK를 반환한다.")
        void registerStock_Success() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            int stockQuantity = 100;
            StockRequestDto request = new StockRequestDto(productId, stockQuantity);
            Stock mockStock = new Stock(UUID.randomUUID(), productId, stockQuantity);
            StockResponseDto mockResponseDto = new StockResponseDto(productId, stockQuantity, LocalDateTime.now());

            given(stockService.registerStock(eq(productId), eq(stockQuantity))).willReturn(mockStock);

            try (MockedStatic<StockDtoMapper> mockedMapper = Mockito.mockStatic(StockDtoMapper.class)) {
                mockedMapper.when(() -> StockDtoMapper.toStockResponseDto(any(Stock.class))).thenReturn(mockResponseDto);

                // when & then
                mockMvc.perform(post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("COMMON200"))
                        .andExpect(jsonPath("$.result.productId").value(productId.toString()))
                        .andExpect(jsonPath("$.result.stockQuantity").value(stockQuantity))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("재고 조회 (GET /api/v1/stock/{productId})")
    class GetStockTest {

        @Test
        @DisplayName("성공 - 존재하는 제품 ID로 조회 시, 재고 정보와 200 OK를 반환한다.")
        void getStock_Success() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            Stock mockStock = new Stock(UUID.randomUUID(), productId, 100);
            StockResponseDto mockResponseDto = new StockResponseDto(productId, 100, LocalDateTime.now());

            given(stockService.getStock(eq(productId))).willReturn(mockStock);

            try (MockedStatic<StockDtoMapper> mockedMapper = Mockito.mockStatic(StockDtoMapper.class)) {
                mockedMapper.when(() -> StockDtoMapper.toStockResponseDto(any(Stock.class))).thenReturn(mockResponseDto);

                // when & then
                mockMvc.perform(get(BASE_URL + "/{productId}", productId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("COMMON200"))
                        .andExpect(jsonPath("$.result.productId").value(productId.toString()))
                        .andExpect(jsonPath("$.result.stockQuantity").value(100))
                        .andDo(print());
            }
        }

        @Test
        @DisplayName("실패 (예외) - 존재하지 않는 제품 ID로 조회 시 404 Not Found를 반환한다.")
        void getStock_NotFound_Fails() throws Exception {
            // given
            UUID nonExistentId = UUID.randomUUID();
            given(stockService.getStock(eq(nonExistentId)))
                    .willThrow(new GeneralException(ErrorStatus.PRODUCT_NOT_FOUND));

            // when & then
            mockMvc.perform(get(BASE_URL + "/{productId}", nonExistentId))
                    .andExpect(status().isNotFound())
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("재고 수정 (PUT /api/v1/stock/{productId})")
    class UpdateStockTest {

        @Test
        @DisplayName("성공 - 올바른 요청 시, 수정된 재고 정보와 200 OK를 반환한다.")
        void updateStock_Success() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            int newQuantity = 50;
            Stock mockStock = new Stock(UUID.randomUUID(), productId, newQuantity);
            StockResponseDto mockResponseDto = new StockResponseDto(productId, newQuantity, LocalDateTime.now());

            given(stockService.updateStock(eq(productId), eq(newQuantity))).willReturn(mockStock);

            try (MockedStatic<StockDtoMapper> mockedMapper = Mockito.mockStatic(StockDtoMapper.class)) {
                mockedMapper.when(() -> StockDtoMapper.toStockResponseDto(any(Stock.class))).thenReturn(mockResponseDto);

                // when & then
                mockMvc.perform(put(BASE_URL + "/{productId}", productId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newQuantity)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("COMMON200"))
                        .andExpect(jsonPath("$.result.productId").value(productId.toString()))
                        .andExpect(jsonPath("$.result.stockQuantity").value(newQuantity))
                        .andDo(print());
            }
        }
    }

    @Nested
    @DisplayName("재고 삭제 (DELETE /api/v1/stock/{productId})")
    class DeleteStockTest {

        @Test
        @DisplayName("성공 - 존재하는 제품 ID로 삭제 시, 제품 ID와 200 OK를 반환한다.")
        void deleteStock_Success() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            Stock mockStock = new Stock(UUID.randomUUID(), productId, 0);
            given(stockService.deleteStock(eq(productId))).willReturn(mockStock);

            // when & then
            mockMvc.perform(delete(BASE_URL + "/{productId}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result").value(productId.toString()))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (예외) - 존재하지 않는 제품 ID로 삭제 시 404 Not Found를 반환한다.")
        void deleteStock_NotFound_Fails() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            doThrow(new GeneralException(ErrorStatus.PRODUCT_NOT_FOUND)).when(stockService).deleteStock(eq(productId));

            // when & then
            mockMvc.perform(delete(BASE_URL + "/{productId}", productId))
                    .andExpect(status().isNotFound())
                    .andDo(print());
        }
    }
}