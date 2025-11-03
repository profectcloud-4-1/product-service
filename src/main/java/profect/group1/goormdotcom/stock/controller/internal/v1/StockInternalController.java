package profect.group1.goormdotcom.stock.controller.internal.v1;

import org.hibernate.StaleObjectStateException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import profect.group1.goormdotcom.common.apiPayload.code.status.ErrorStatus;
import profect.group1.goormdotcom.stock.controller.internal.v1.dto.ProductStockAdjustmentRequestDto;
import profect.group1.goormdotcom.stock.controller.internal.v1.dto.StockAdjustmentRequestDto;
import profect.group1.goormdotcom.stock.controller.internal.v1.dto.StockAdjustmentResponseDto;
import profect.group1.goormdotcom.stock.domain.exception.InsufficientStockException;
import profect.group1.goormdotcom.stock.service.StockService;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.apiPayload.code.status.SuccessStatus;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/internal/v1/stock")
@RequiredArgsConstructor
public class StockInternalController implements StockInternalApiDocs {

    private final StockService stockService;


    @PostMapping("/decrease")
    public ApiResponse<StockAdjustmentResponseDto> decreaseStocks(
            @RequestBody @Valid StockAdjustmentRequestDto stockAdjustmentRequestDto
    ) {
        Map<UUID, Integer> requestedQuantityMap = new HashMap<UUID, Integer>();
        for (ProductStockAdjustmentRequestDto dto : stockAdjustmentRequestDto.products()) {
            requestedQuantityMap.put(dto.productId(), dto.requestedStockQuantity());
        }

        // 재고 차감 시도
        Boolean status;
        try {
            status = stockService.decreaseStocks(requestedQuantityMap);
            if (status) {
                // 재고 차감 성공 시
                return ApiResponse.of(SuccessStatus._OK, new StockAdjustmentResponseDto(status, new ArrayList<UUID>(requestedQuantityMap.keySet())));
            } else {
                // 실패시
                return ApiResponse.onFailure(
                        ErrorStatus._CONFLICT.getCode(),
                        ErrorStatus._CONFLICT.getMessage(),
                        new StockAdjustmentResponseDto(false, new ArrayList<>(requestedQuantityMap.keySet()))
                );
            }
        } catch (InsufficientStockException e) {
            return ApiResponse.onFailure(
                    ErrorStatus._INSUFFICIENT_STOCK_QUANTITY.getCode() ,
                    ErrorStatus._INSUFFICIENT_STOCK_QUANTITY.getMessage(),
                    new StockAdjustmentResponseDto(false, new ArrayList<UUID>(requestedQuantityMap.keySet()))
            );
        } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
            return ApiResponse.onFailure(
                    ErrorStatus._ADJUST_STOCK_FAILED.getCode() ,
                    ErrorStatus._ADJUST_STOCK_FAILED.getMessage(),
                    new StockAdjustmentResponseDto(false, new ArrayList<UUID>(requestedQuantityMap.keySet()))
            );
        }
    }

    @PostMapping("/increase")
    public ApiResponse<StockAdjustmentResponseDto> increaseStocks(
            @RequestBody @Valid StockAdjustmentRequestDto stockAdjustmentRequestDto
    ) {
        Map<UUID, Integer> requestedQuantityMap = new HashMap<UUID, Integer>();
        for (ProductStockAdjustmentRequestDto dto : stockAdjustmentRequestDto.products()) {
            requestedQuantityMap.put(dto.productId(), dto.requestedStockQuantity());
        }

        Boolean status;
        try {
            // 재고 증가
            status = stockService.increaseStocks(requestedQuantityMap);
            if (status) {
                // 재고 증가 성공 시
                return ApiResponse.of(SuccessStatus._OK, new StockAdjustmentResponseDto(status, new ArrayList<UUID>(requestedQuantityMap.keySet())));
            } else {
                // 실패시
                return ApiResponse.onFailure(
                        ErrorStatus._CONFLICT.getCode(),
                        ErrorStatus._CONFLICT.getMessage(),
                        new StockAdjustmentResponseDto(false, new ArrayList<>(requestedQuantityMap.keySet()))
                );
            }
        } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
            return ApiResponse.onFailure(
                    ErrorStatus._ADJUST_STOCK_FAILED.getCode() ,
                    ErrorStatus._ADJUST_STOCK_FAILED.getMessage(),
                    new StockAdjustmentResponseDto(false, new ArrayList<UUID>(requestedQuantityMap.keySet()))
            );
        }
    }


}
