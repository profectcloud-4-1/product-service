package profect.group1.goormdotcom.product.controller.internal.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestParam;
import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.product.controller.internal.v1.dto.CartProductResponseDto;

import java.util.List;
import java.util.UUID;

@Tag(name = "상품(내부)", description = "내부 상품 API")
public interface ProductInternalApiDocs {

    @Operation(
            summary = "카트용 상품 요약 조회",
            description = "카트 화면용으로 상품 이름/가격/이미지/매진 여부를 반환합니다. 삭제되었거나 존재하지 않는 상품도 포함됩니다. \n" +
                    "쿼리 파라미터로 여러 ID를 반복 전달하세요. 예: ?id={uuid1}&id={uuid2}",
            security = { @SecurityRequirement(name = "User-Id"), @SecurityRequirement(name = "User-Roles") }
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "성공",
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(name = "success", value = "{\n  \"code\": \"COMMON200\",\n  \"message\": \"성공입니다.\",\n  \"result\": [\n    {\n      \"productId\": \"9f3d3a7f-1f7a-4a17-9f7c-3b1bb1b1b1b1\",\n      \"productName\": \"티셔츠\",\n      \"price\": 19900,\n      \"imageUrl\": \"https://cdn.example.com/img.jpg\",\n      \"isSoldOut\": false\n    },\n    {\n      \"productId\": \"1e2d3c4b-5a6f-7081-92a3-b4c5d6e7f809\",\n      \"productName\": null,\n      \"price\": 0,\n      \"imageUrl\": \"\",\n      \"isSoldOut\": false\n    }\n  ]\n}"))
    )
    ApiResponse<List<CartProductResponseDto>> getCartProducts(
            @Parameter(description = "상품 ID (여러 개 전달 시 ?id=...&id=... 형태)")
            @RequestParam(value = "id") List<UUID> productIds
    );
}

