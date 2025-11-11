package profect.group1.goormdotcom.product.controller;

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
import profect.group1.goormdotcom.common.apiPayload.exceptions.handler.ProductHandler;
import profect.group1.goormdotcom.common.file.FileStorageManager;
import profect.group1.goormdotcom.common.file.domain.FileDomain;
import profect.group1.goormdotcom.common.file.dto.PresignedUrlResponse;
import profect.group1.goormdotcom.common.file.dto.UploadUrlRequest;
import profect.group1.goormdotcom.product.controller.external.v1.ProductExternalController;
import profect.group1.goormdotcom.product.controller.external.v1.dto.DeleteProductRequestDto;
import profect.group1.goormdotcom.product.controller.external.v1.dto.ProductRequestDto;
import profect.group1.goormdotcom.product.controller.external.v1.dto.ProductResponseDto;
import profect.group1.goormdotcom.product.controller.external.v1.dto.UpdateProductRequestDto;
import profect.group1.goormdotcom.product.controller.external.v1.mapper.ProductDtoMapper;
import profect.group1.goormdotcom.product.domain.Product;
import profect.group1.goormdotcom.product.service.ProductService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductExternalController 단위 테스트")
public class ProductExternalControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProductExternalController productExternalController;

    @Mock
    private ProductService productService;

    @Mock
    private FileStorageManager fileStorageManager;

    private final String BASE_URL = "/api/v1/product";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productExternalController)
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    @Nested
    @DisplayName("제품 등록 (POST /api/v1/product/register)")
    class RegisterProductTest {

        @Test
        @DisplayName("성공 - 올바른 요청 시, 생성된 제품 ID와 200 OK를 반환한다.")
        void registerProduct_Success() throws Exception {
            // given
            ProductRequestDto request = new ProductRequestDto(
                    "신제품", UUID.randomUUID(), UUID.randomUUID(), "설명", 10000, 100, List.of(UUID.randomUUID())
            );
            UUID newProductId = UUID.randomUUID();
            given(productService.createProduct(any(), any(), any(), anyInt(), anyInt(), any(), any())).willReturn(newProductId);

            // when & then
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result").value(newProductId.toString()))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (유효성) - 제품명이 비어있으면 400 Bad Request를 반환한다.")
        void registerProduct_WithBlankName_Fails() throws Exception {
            // given
            ProductRequestDto request = new ProductRequestDto(
                    "", UUID.randomUUID(), UUID.randomUUID(), "설명", 10000, 100, List.of(UUID.randomUUID())
            );

            // when & then
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 - 가격이 음수이면 400 Bad Request를 반환한다.")
        void registerProduct_WithNegativePrice_Fails() throws Exception {
            // given
            ProductRequestDto request = new ProductRequestDto(
                    "신제품", UUID.randomUUID(), UUID.randomUUID(), "설명", -100, 100, List.of(UUID.randomUUID())
            );

            // when & then
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (유효성) - 이미지 ID 목록이 비어있으면 400 Bad Request를 반환한다.")
        void registerProduct_WithEmptyImageIds_Fails() throws Exception {
            // given
            ProductRequestDto request = new ProductRequestDto(
                    "신제품", UUID.randomUUID(), UUID.randomUUID(), "설명", 10000, 100, Collections.emptyList()
            );

            // when & then
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (예외) - Service 로직에서 예외가 발생하면, 해당 예외에 맞는 상태코드를 반환한다.")
        void registerProduct_ServiceThrowsException_Fails() throws Exception {
            // given
            ProductRequestDto request = new ProductRequestDto("신제품", UUID.randomUUID(), UUID.randomUUID(), "설명", 10000, 100, List.of(UUID.randomUUID()));
            given(productService.createProduct(any(), any(), any(), anyInt(), anyInt(), any(), any()))
                    .willThrow(new ProductHandler(ErrorStatus.CATEGORY_NOT_FOUND));

            // when & then
            mockMvc.perform(post(BASE_URL + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("제품 조회 (GET /api/v1/product/{productId})")
    class GetProductTest {

        @Test
        @DisplayName("성공 - 존재하는 제품 ID로 조회 시, 제품 정보와 200 OK를 반환한다.")
        void getProduct_Success() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            Product mockProduct = new Product(productId, UUID.randomUUID(), UUID.randomUUID(), "조회 제품", "설명", 10000, null, null, null, Collections.emptyList());
            ProductResponseDto mockResponseDto = new ProductResponseDto("조회 제품", mockProduct.getBrandId(), mockProduct.getCategoryId(), "설명", 10000, Collections.emptyList());

            given(productService.getProduct(eq(productId))).willReturn(mockProduct);

            try (MockedStatic<ProductDtoMapper> mockedMapper = Mockito.mockStatic(ProductDtoMapper.class)) {
                mockedMapper.when(() -> ProductDtoMapper.toProductResponseDto(any(Product.class))).thenReturn(mockResponseDto);

                // when & then
                mockMvc.perform(get(BASE_URL + "/{productId}", productId)
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("COMMON200"))
                        .andExpect(jsonPath("$.result.name").value("조회 제품"))
                        .andDo(print());
            }
        }

        @Test
        @DisplayName("실패 (예외) - 존재하지 않는 제품 ID로 조회 시 404 Not Found를 반환한다.")
        void getProduct_NotFound_Fails() throws Exception {
            // given
            UUID nonExistentId = UUID.randomUUID();
            given(productService.getProduct(eq(nonExistentId)))
                    .willThrow(new ProductHandler(ErrorStatus.PRODUCT_NOT_FOUND));

            // when & then
            mockMvc.perform(get(BASE_URL + "/{productId}", nonExistentId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("제품 수정 (PUT /api/v1/product/{productId})")
    class UpdateProductTest {

        @Test
        @DisplayName("성공 - 올바른 요청 시, 수정된 제품 정보와 200 OK를 반환한다.")
        void updateProduct_Success() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            UpdateProductRequestDto request = new UpdateProductRequestDto(
                    "수정된 제품명", UUID.randomUUID(), UUID.randomUUID(), "수정 설명", 12000, List.of(UUID.randomUUID())
            );
            Product updatedProduct = new Product(productId, request.brandId(), request.categoryId(), request.name(), request.description(), request.price(), null, null, null, Collections.emptyList());
            ProductResponseDto mockResponseDto = new ProductResponseDto(request.name(), request.brandId(), request.categoryId(), request.description(), request.price(), Collections.emptyList());

            given(productService.updateProduct(eq(productId), any(), any(), any(), anyInt(), any(), any()))
                    .willReturn(updatedProduct);

            try (MockedStatic<ProductDtoMapper> mockedMapper = Mockito.mockStatic(ProductDtoMapper.class)) {
                mockedMapper.when(() -> ProductDtoMapper.toProductResponseDto(any(Product.class))).thenReturn(mockResponseDto);

                // when & then
                mockMvc.perform(put(BASE_URL + "/{productId}", productId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("COMMON200"))
                        .andExpect(jsonPath("$.result.name").value("수정된 제품명"))
                        .andDo(print());
            }
        }

        @Test
        @DisplayName("실패 (유효성) - 제품명이 비어있으면 400 Bad Request를 반환한다.")
        void updateProduct_WithBlankName_Fails() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            UpdateProductRequestDto request = new UpdateProductRequestDto(
                    "", UUID.randomUUID(), UUID.randomUUID(), "설명", 10000, List.of(UUID.randomUUID())
            );

            // when & then
            mockMvc.perform(put(BASE_URL + "/{productId}", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (예외) - 존재하지 않는 제품 ID를 수정 시 404 Not Found를 반환한다.")
        void updateProduct_NotFound_Fails() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            UpdateProductRequestDto request = new UpdateProductRequestDto(
                    "수정된 제품명", UUID.randomUUID(), UUID.randomUUID(), "수정 설명", 12000, List.of(UUID.randomUUID())
            );
            given(productService.updateProduct(eq(productId), any(), any(), any(), anyInt(), any(), any()))
                    .willThrow(new GeneralException(ErrorStatus.PRODUCT_NOT_FOUND));

            // when & then
            mockMvc.perform(put(BASE_URL + "/{productId}", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("제품 삭제 (DELETE /api/v1/product/{productId})")
    class DeleteProductTest {

        @Test
        @DisplayName("성공 - 존재하는 제품 ID로 삭제 시, 200 OK를 반환한다.")
        void deleteProduct_Success() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            UUID brandId = UUID.randomUUID();
            String requestBody = objectMapper.writeValueAsString(brandId);

            doNothing().when(productService).deleteProduct(eq(productId), eq(brandId));

            // when & then
            mockMvc.perform(delete(BASE_URL + "/{productId}", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result").value(productId.toString()))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (예외) - 존재하지 않는 제품 ID로 삭제 시 404 Not Found를 반환한다.")
        void deleteProduct_NotFound_Fails() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            UUID brandId = UUID.randomUUID();
            String requestBody = objectMapper.writeValueAsString(brandId);

            doThrow(new GeneralException(ErrorStatus.PRODUCT_NOT_FOUND)).when(productService).deleteProduct(eq(productId), eq(brandId));

            // when & then
            mockMvc.perform(delete(BASE_URL + "/{productId}", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound())
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("다중 제품 삭제 (DELETE /api/v1/product/)")
    class DeleteProductsTest {

        @Test
        @DisplayName("성공 - 올바른 요청 시, 삭제된 제품 ID 목록과 200 OK를 반환한다.")
        void deleteProducts_Success() throws Exception {
            // given
            List<UUID> productIds = List.of(UUID.randomUUID(), UUID.randomUUID());
            UUID brandId = UUID.randomUUID();
            DeleteProductRequestDto request = new DeleteProductRequestDto(productIds, brandId);

            doNothing().when(productService).deleteProducts(eq(productIds), eq(brandId));

            // when & then
            mockMvc.perform(delete(BASE_URL + "/")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result").isArray())
                    .andExpect(jsonPath("$.result[0]").value(productIds.get(0).toString()))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("제품 이미지 삭제 (DELETE /api/v1/product/image/{imageId})")
    class DeleteProductImageTest {

        @Test
        @DisplayName("성공 - 존재하는 이미지 ID로 삭제 시, 200 OK를 반환한다.")
        void deleteProductImage_Success() throws Exception {
            // given
            UUID imageId = UUID.randomUUID();
            doNothing().when(productService).deleteProductImage(eq(imageId));

            // when & then
            mockMvc.perform(delete(BASE_URL + "/image/{imageId}", imageId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result").value(imageId.toString()))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (예외) - 존재하지 않는 이미지 ID로 삭제 시 404 Not Found를 반환한다.")
        void deleteProductImage_NotFound_Fails() throws Exception {
            // given
            UUID imageId = UUID.randomUUID();
            doThrow(new ProductHandler(ErrorStatus.IMAGE_NOT_FOUND)).when(productService).deleteProductImage(eq(imageId));

            // when & then
            mockMvc.perform(delete(BASE_URL + "/image/{imageId}", imageId)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("이미지 관련 엔드포인트")
    class ImageEndpointsTest {

        @Test
        @DisplayName("성공 - 이미지 업로드 URL 생성")
        void generateImageUploadUrl_Success() throws Exception {
            // given
            UploadUrlRequest request = new UploadUrlRequest("test.jpg", FileDomain.PRODUCT, "image/jpeg");
            PresignedUrlResponse response = PresignedUrlResponse.builder()
                    .fileId(UUID.randomUUID())
                    .presignedUrl("http://presigned-url.com")
                    .build();
            given(fileStorageManager.generateUploadUrl(anyString(), any(FileDomain.class), anyString())).willReturn(response);

            // when & then
            mockMvc.perform(post(BASE_URL + "/images/upload-url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.presignedUrl").value("http://presigned-url.com"));
        }

        @Test
        @DisplayName("성공 - 이미지 업로드 확정")
        void confirmImageUpload_Success() throws Exception {
            // given
            UUID fileId = UUID.randomUUID();
            doNothing().when(fileStorageManager).confirmUpload(fileId);

            // when & then
            mockMvc.perform(post(BASE_URL + "/images/{fileId}/confirm", fileId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("confirmed"));
        }

        @Test
        @DisplayName("성공 - 이미지 URL 조회")
        void getImageObjectKey_Success() throws Exception {
            // given
            UUID fileId = UUID.randomUUID();
            String objectKey = "path/to/image.jpg";
            given(fileStorageManager.getObjectKey(fileId)).willReturn(objectKey);

            // when & then
            mockMvc.perform(get(BASE_URL + "/images/{fileId}/url", fileId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.objectKey").value(objectKey));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 이미지 URL 조회 시 400 Bad Request 반환")
        void getImageObjectKey_NotFound_Fails() throws Exception {
            // given
            UUID fileId = UUID.randomUUID();
            given(fileStorageManager.getObjectKey(fileId))
                    .willThrow(new IllegalArgumentException("파일을 찾을 수 없습니다."));

            // when & then
            mockMvc.perform(get(BASE_URL + "/images/{fileId}/url", fileId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON400"))
                    .andDo(print());
        }
    }
}