package profect.group1.goormdotcom.stock.controller.external.v1;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import profect.group1.goormdotcom.stock.controller.external.v1.dto.StockResponseDto;
import profect.group1.goormdotcom.stock.controller.external.v1.dto.StockRequestDto;
import profect.group1.goormdotcom.stock.controller.mapper.StockDtoMapper;
import profect.group1.goormdotcom.stock.domain.Stock;
import profect.group1.goormdotcom.stock.service.StockService;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.apiPayload.code.status.SuccessStatus;

import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/api/v1/stock")
@RequiredArgsConstructor
@Slf4j
public class StockExternalController implements StockApiDocs {

    private final StockService stockService;

    @PostMapping
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<StockResponseDto> registerStock(
        @RequestBody StockRequestDto stockRequestDto
    ) {
        Stock stock = stockService.registerStock(stockRequestDto.productId(), stockRequestDto.stockQuantity());
        return ApiResponse.of(SuccessStatus._OK, StockDtoMapper.toStockResponseDto(stock));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<StockResponseDto> updateStock(
        @PathVariable(value="productId") UUID productId, 
        @RequestBody int stockQuantity
    ) {
        Stock stock = stockService.updateStock(productId, stockQuantity);
        return ApiResponse.of(SuccessStatus._OK, StockDtoMapper.toStockResponseDto(stock));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<StockResponseDto> getStock(
        @PathVariable(value = "productId") UUID productId
    ) {
        
        Stock stock = stockService.getStock(productId);
        return ApiResponse.of(SuccessStatus._OK, StockDtoMapper.toStockResponseDto(stock));        
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<UUID> deleteStock(
        @PathVariable(value = "productId") UUID productId
    ) {
        Stock stock = stockService.deleteStock(productId);
        return ApiResponse.of(SuccessStatus._OK, stock.getProductId());
    }

}
