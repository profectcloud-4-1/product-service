package profect.group1.goormdotcom.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import profect.group1.goormdotcom.common.apiPayload.code.status.ErrorStatus;
import profect.group1.goormdotcom.common.apiPayload.exceptions.ExceptionAdvice;
import profect.group1.goormdotcom.common.apiPayload.exceptions.GeneralException;
import profect.group1.goormdotcom.review.controller.external.v1.ReviewExternalController;
import profect.group1.goormdotcom.review.controller.external.v1.dto.CreateReviewRequestDto;
import profect.group1.goormdotcom.review.controller.external.v1.dto.ProductReviewListResponseDto;
import profect.group1.goormdotcom.review.controller.external.v1.dto.ReviewResponseDto;
import profect.group1.goormdotcom.review.controller.external.v1.dto.UpdatedReviewRequestDto;
import profect.group1.goormdotcom.review.service.ReviewService;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewExternalController 단위 테스트")
public class ReviewControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ReviewExternalController reviewExternalController;

    @Mock
    private ReviewService reviewService;

    private final String BASE_URL = "/api/v1/reviews";
    private final UUID DUMMY_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reviewExternalController)
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    private CreateReviewRequestDto createCreateReviewRequestDto(UUID productId, int rating, String content, UUID fileId) {
        CreateReviewRequestDto dto = new CreateReviewRequestDto();
        ReflectionTestUtils.setField(dto, "productId", productId);
        ReflectionTestUtils.setField(dto, "rating", rating);
        ReflectionTestUtils.setField(dto, "content", content);
        ReflectionTestUtils.setField(dto, "fileId", fileId);
        return dto;
    }

    private UpdatedReviewRequestDto createUpdatedReviewRequestDto(Integer rating, String content, String imageUrl) {
        UpdatedReviewRequestDto dto = new UpdatedReviewRequestDto();
        ReflectionTestUtils.setField(dto, "rating", rating);
        ReflectionTestUtils.setField(dto, "content", content);
        ReflectionTestUtils.setField(dto, "imageUrl", imageUrl);
        return dto;
    }

    @Nested
    @DisplayName("리뷰 작성 (POST /api/v1/reviews)")
    class CreateReviewTest {

        @Test
        @DisplayName("성공 - 올바른 요청 시, 생성된 리뷰 정보와 200 OK를 반환한다.")
        void createReview_Success() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            UUID reviewId = UUID.randomUUID();
            CreateReviewRequestDto request = createCreateReviewRequestDto(productId, 5, "This is a great product!", null);
            ReviewResponseDto mockResponse = new ReviewResponseDto(reviewId, 5, DUMMY_USER_ID, "This is a great product!", null, LocalDateTime.now());
            given(reviewService.createReview(any(CreateReviewRequestDto.class), eq(DUMMY_USER_ID))).willReturn(mockResponse);

            // when & then
            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Id", DUMMY_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result.reviewId").value(mockResponse.getReviewId().toString()))
                    .andExpect(jsonPath("$.result.rating").value(mockResponse.getRating()))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (유효성 검사) - 내용이 10자 미만이면 400 Bad Request를 반환한다.")
        void createReview_InvalidContent_Fails() throws Exception {
            // given
            UUID productId = UUID.randomUUID();
            CreateReviewRequestDto request = createCreateReviewRequestDto(productId, 5, "short", null); // 10자 미만
            // when & then
            mockMvc.perform(post(BASE_URL)
                            .header("X-User-Id", DUMMY_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorStatus._BAD_REQUEST.getCode()))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("리뷰 수정 (PUT /api/v1/reviews/{reviewId})")
    class UpdateReviewTest {
        private final UUID reviewId = UUID.randomUUID();

        @Test
        @DisplayName("성공 - 올바른 요청 시, 수정된 리뷰 정보와 200 OK를 반환한다.")
        void updateReview_Success() throws Exception {
            // given
            UpdatedReviewRequestDto request = createUpdatedReviewRequestDto(4, "updated content for review", null);
            ReviewResponseDto mockResponse = new ReviewResponseDto(reviewId, 4, DUMMY_USER_ID, "updated content for review", null, LocalDateTime.now());
            given(reviewService.updateReview(eq(reviewId), any(UpdatedReviewRequestDto.class), eq(DUMMY_USER_ID))).willReturn(mockResponse);

            // when & then
            mockMvc.perform(put(BASE_URL + "/{reviewId}", reviewId)
                            .header("X-User-Id", DUMMY_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result.content").value("updated content for review"))
                    .andExpect(jsonPath("$.result.rating").value(mockResponse.getRating()))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (권한 없음) - 다른 사람의 리뷰 수정 시 200 OK와 FORBIDDEN 코드를 반환한다.")
        void updateReview_Forbidden_Fails() throws Exception {
            // given
            UpdatedReviewRequestDto request = createUpdatedReviewRequestDto(4, "updated content", null);
            given(reviewService.updateReview(eq(reviewId), any(UpdatedReviewRequestDto.class), eq(DUMMY_USER_ID)))
                    .willThrow(new GeneralException(ErrorStatus._FORBIDDEN));

            // when & then
            mockMvc.perform(put(BASE_URL + "/{reviewId}", reviewId)
                            .header("X-User-Id", DUMMY_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(ErrorStatus._FORBIDDEN.getCode()))
                    .andExpect(jsonPath("$.message").value(ErrorStatus._FORBIDDEN.getMessage()))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("리뷰 삭제 (DELETE /api/v1/reviews/{reviewId})")
    class DeleteReviewTest {
        private final UUID reviewId = UUID.randomUUID();

        @Test
        @DisplayName("성공 - 자신의 리뷰 삭제 시, 성공 메시지와 200 OK를 반환한다.")
        void deleteReview_Success() throws Exception {
            // given
            doNothing().when(reviewService).deleteReview(eq(reviewId), eq(DUMMY_USER_ID));

            // when & then
            mockMvc.perform(delete(BASE_URL + "/{reviewId}", reviewId)
                            .header("X-User-Id", DUMMY_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result").value("Deleted."))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 (찾을 수 없음) - 존재하지 않는 리뷰 삭제 시 404 Not Found를 반환한다.")
        void deleteReview_NotFound_Fails() throws Exception {
            // given
            doThrow(new GeneralException(ErrorStatus._NOT_FOUND))
                    .when(reviewService).deleteReview(eq(reviewId), eq(DUMMY_USER_ID));

            // when & then
            mockMvc.perform(delete(BASE_URL + "/{reviewId}", reviewId)
                            .header("X-User-Id", DUMMY_USER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorStatus._NOT_FOUND.getCode()))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("상품별 리뷰 목록 조회 (GET /api/v1/reviews/products/{productId})")
    class GetProductReviewsTest {
        private final UUID productId = UUID.randomUUID();

        @Test
        @DisplayName("성공 - 상품의 리뷰 목록 조회 시, 리뷰 목록과 200 OK를 반환한다.")
        void getProductReviews_Success() throws Exception {
            // given
            ProductReviewListResponseDto mockResponse = new ProductReviewListResponseDto(
                    productId, 4.5, 1, Collections.emptyList(), 0, 10, 1L, 1
            );
            given(reviewService.getProductReviews(eq(productId), eq(0), eq(10), eq("createdAt")))
                    .willReturn(mockResponse);

            // when & then
            mockMvc.perform(get(BASE_URL + "/products/{productId}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.result.averageRating").value(4.5))
                    .andDo(print());
        }
    }
}