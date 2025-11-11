package profect.group1.goormdotcom.review;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import profect.group1.goormdotcom.review.controller.external.v1.dto.CreateReviewRequestDto;
import profect.group1.goormdotcom.review.controller.external.v1.dto.ProductReviewListResponseDto;
import profect.group1.goormdotcom.review.controller.external.v1.dto.UpdatedReviewRequestDto;
import profect.group1.goormdotcom.review.infrastructure.client.OrderClient;
import profect.group1.goormdotcom.review.infrastructure.client.PresignedClient;
import profect.group1.goormdotcom.review.infrastructure.client.dto.ObjectKeyResponseDto;
import profect.group1.goormdotcom.review.repository.ReviewImageRepository;
import profect.group1.goormdotcom.review.repository.ReviewRepository;
import profect.group1.goormdotcom.review.repository.entity.ReviewEntity;
import profect.group1.goormdotcom.review.repository.entity.ReviewImageEntity;
import profect.group1.goormdotcom.review.service.ReviewService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService 비즈니스 로직 테스트")
public class ReviewServiceTest {

    @InjectMocks
    private ReviewService reviewService;

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewImageRepository reviewImageRepository;
    @Mock
    private OrderClient orderClient;
    @Mock
    private PresignedClient presignedClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reviewService, "cloudfrontDomain", "https://test.cloudfront.net");
    }

    //stub 헬퍼 메소드
    private CreateReviewRequestDto createCreateReviewRequestDto(UUID productId, int rating, String content, UUID fileId) {
        CreateReviewRequestDto dto = new CreateReviewRequestDto();
        ReflectionTestUtils.setField(dto, "productId", productId);
        ReflectionTestUtils.setField(dto, "rating", rating);
        ReflectionTestUtils.setField(dto, "content", content);
        ReflectionTestUtils.setField(dto, "fileId", fileId);
        return dto;
    }

    private UpdatedReviewRequestDto createUpdatedReviewRequestDto(Integer rating, String content) {
        UpdatedReviewRequestDto dto = new UpdatedReviewRequestDto();
        ReflectionTestUtils.setField(dto, "rating", rating);
        ReflectionTestUtils.setField(dto, "content", content);
        return dto;
    }

    private void given_주문이_존재한다(UUID userId, UUID productId, UUID orderId) {
        when(orderClient.getOrderIdByUserAndProduct(userId, productId)).thenReturn(orderId);
    }

    private void given_주문이_존재하지_않는다(UUID userId, UUID productId) {
        when(orderClient.getOrderIdByUserAndProduct(userId, productId)).thenThrow(FeignException.NotFound.class);
    }

    private void given_리뷰가_존재하지_않는다(UUID orderId) {
        when(reviewRepository.existsByOrderId(orderId)).thenReturn(false);
    }

    private void given_리뷰가_이미_존재한다(UUID orderId) {
        when(reviewRepository.existsByOrderId(orderId)).thenReturn(true);
    }

    private ReviewEntity given_리뷰가_존재하며_사용자소유이다(UUID reviewId, UUID userId) {
        ReviewEntity reviewEntity = new ReviewEntity(reviewId, userId, UUID.randomUUID(), UUID.randomUUID(), 5, "content", LocalDateTime.now(), null, null);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(reviewEntity));
        return reviewEntity;
    }

    private void given_리뷰가_존재하지만_사용자소유가_아니다(UUID reviewId, UUID userId) {
        UUID ownerId = UUID.randomUUID();
        while (ownerId.equals(userId)) {
            ownerId = UUID.randomUUID();
        }
        ReviewEntity reviewEntity = new ReviewEntity(reviewId, ownerId, UUID.randomUUID(), UUID.randomUUID(), 5, "content", LocalDateTime.now(), null, null);
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(reviewEntity));
    }

    private void given_리뷰를_찾을수_없다(UUID reviewId) {
        when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());
    }

    @Nested
    @DisplayName("리뷰 생성 시나리오")
    class CreateReviewTest {

        private final UUID userId = UUID.randomUUID();
        private final UUID productId = UUID.randomUUID();
        private final UUID orderId = UUID.randomUUID();

        @Test
        @DisplayName("성공 - 이미지 없이 리뷰를 생성한다.")
        void createReview_Success_WithoutImage() {
            // given
            CreateReviewRequestDto request = createCreateReviewRequestDto(productId, 5, "test product review content", null);
            given_주문이_존재한다(userId, productId, orderId);
            given_리뷰가_존재하지_않는다(orderId);
            when(reviewRepository.save(any(ReviewEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            reviewService.createReview(request, userId);

            // then
            verify(orderClient, times(1)).getOrderIdByUserAndProduct(userId, productId);
            verify(reviewRepository, times(1)).existsByOrderId(orderId);
            verify(reviewRepository, times(1)).save(any(ReviewEntity.class));
            verify(reviewImageRepository, never()).save(any(ReviewImageEntity.class));
            verify(presignedClient, never()).confirmUpload(any());
        }

        @Test
        @DisplayName("성공 - 이미지와 함께 리뷰를 생성한다.")
        void createReview_Success_WithImage() {
            // given
            UUID fileId = UUID.randomUUID();
            CreateReviewRequestDto request = createCreateReviewRequestDto(productId, 5, "test product review content", fileId);
            given_주문이_존재한다(userId, productId, orderId);
            given_리뷰가_존재하지_않는다(orderId);
            when(reviewRepository.save(any(ReviewEntity.class))).thenAnswer(invocation -> {
                ReviewEntity entity = invocation.getArgument(0);
                return new ReviewEntity(entity.getId(), entity.getUserId(), entity.getProductId(), entity.getOrderId(), entity.getRating(), entity.getContent(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getDeletedAt());
            });
            when(reviewImageRepository.save(any(ReviewImageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            reviewService.createReview(request, userId);

            // then
            verify(reviewRepository, times(1)).save(any(ReviewEntity.class));
            verify(reviewImageRepository, times(1)).save(any(ReviewImageEntity.class));
            verify(presignedClient, times(1)).confirmUpload(fileId);
        }

        @Test
        @DisplayName("성공 (PresignedClient 실패) - 이미지 생성 후 PresignedClient 호출에 실패해도 예외를 던지지 않는다.")
        void createReview_PresignedClientFails_LogsError() {
            // given
            UUID fileId = UUID.randomUUID();
            CreateReviewRequestDto request = createCreateReviewRequestDto(productId, 5, "test product review content", fileId);
            given_주문이_존재한다(userId, productId, orderId);
            given_리뷰가_존재하지_않는다(orderId);
            when(reviewRepository.save(any(ReviewEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(reviewImageRepository.save(any(ReviewImageEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            doThrow(new RuntimeException("PresignedClient failed")).when(presignedClient).confirmUpload(fileId);

            // when & then
            // 예외가 전파되지 않고 메소드가 정상적으로 완료되는지 확인
            reviewService.createReview(request, userId);
            verify(presignedClient, times(1)).confirmUpload(fileId);
        }

        @Test
        @DisplayName("실패 - 주문 내역이 없으면 예외가 발생한다.")
        void createReview_OrderNotFound_ThrowsException() {
            // given
            CreateReviewRequestDto request = createCreateReviewRequestDto(productId, 5, "This is a great product!", null);
            given_주문이_존재하지_않는다(userId, productId);

            // when & then
            assertThatThrownBy(() -> reviewService.createReview(request, userId))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("실패 - 이미 리뷰를 작성했으면 예외가 발생한다.")
        void createReview_ReviewAlreadyExists_ThrowsException() {
            // given
            CreateReviewRequestDto request = createCreateReviewRequestDto(productId, 5, "This is a great product!", null);
            given_주문이_존재한다(userId, productId, orderId);
            given_리뷰가_이미_존재한다(orderId);

            // when & then
            assertThatThrownBy(() -> reviewService.createReview(request, userId))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("상품별 리뷰 목록 조회 시나리오")
    class GetProductReviewsTest {
        private final UUID productId = UUID.randomUUID();

        @Test
        @DisplayName("성공 - 이미지 없는 리뷰 목록을 조회한다.")
        void getProductReviews_Success_WithoutImages() {
            // given
            ReviewEntity review1 = new ReviewEntity(UUID.randomUUID(), UUID.randomUUID(), productId, UUID.randomUUID(), 5, "content1", LocalDateTime.now(), null, null);
            Page<ReviewEntity> reviewPage = new PageImpl<>(List.of(review1));
            when(reviewRepository.findByProductId(eq(productId), any(Pageable.class))).thenReturn(reviewPage);
            when(reviewImageRepository.findByReviewId(review1.getId())).thenReturn(Optional.empty());
            when(reviewRepository.findAverageRatingByProductId(productId)).thenReturn(5.0);
            when(reviewRepository.countByProductId(productId)).thenReturn(1L);

            // when
            reviewService.getProductReviews(productId, 0, 10, "createdAt");

            // then
            verify(presignedClient, never()).getObjectKey(any());
        }

        @Test
        @DisplayName("성공 - 이미지 있는 리뷰 목록을 조회한다.")
        void getProductReviews_Success_WithImages() {
            // given
            ReviewEntity review1 = new ReviewEntity(UUID.randomUUID(), UUID.randomUUID(), productId, UUID.randomUUID(), 4, "content2", LocalDateTime.now(), null, null);
            ReviewImageEntity image1 = ReviewImageEntity.builder().fileId(UUID.randomUUID()).build();
            ObjectKeyResponseDto objectKeyDto = new ObjectKeyResponseDto("path/to/image.jpg");

            Page<ReviewEntity> reviewPage = new PageImpl<>(List.of(review1));
            when(reviewRepository.findByProductId(eq(productId), any(Pageable.class))).thenReturn(reviewPage);
            when(reviewImageRepository.findByReviewId(review1.getId())).thenReturn(Optional.of(image1));
            when(presignedClient.getObjectKey(image1.getFileId())).thenReturn(ResponseEntity.ok(objectKeyDto));
            when(reviewRepository.findAverageRatingByProductId(productId)).thenReturn(4.0);
            when(reviewRepository.countByProductId(productId)).thenReturn(1L);

            // when
            reviewService.getProductReviews(productId, 0, 10, "createdAt");

            // then
            verify(presignedClient, times(1)).getObjectKey(image1.getFileId());
        }

        @Test
        @DisplayName("성공 - 'rating'으로 정렬하여 조회한다.")
        void getProductReviews_SortByRating_Success() {
            // given
            Page<ReviewEntity> reviewPage = new PageImpl<>(Collections.emptyList());
            when(reviewRepository.findByProductId(eq(productId), any(Pageable.class))).thenReturn(reviewPage);

            // when
            reviewService.getProductReviews(productId, 0, 10, "rating");

            // then
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(reviewRepository).findByProductId(eq(productId), pageableCaptor.capture());
            Pageable capturedPageable = pageableCaptor.getValue();

            assertThat(capturedPageable.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "rating"));
        }
    }

    @Nested
    @DisplayName("리뷰 수정 시나리오")
    class UpdateReviewTest {
        private final UUID userId = UUID.randomUUID();
        private final UUID reviewId = UUID.randomUUID();

        @Test
        @DisplayName("성공 - 자신의 리뷰를 수정한다.")
        void updateReview_Success() {
            // given
            given_리뷰가_존재하며_사용자소유이다(reviewId, userId);
            when(reviewRepository.save(any(ReviewEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            UpdatedReviewRequestDto request = createUpdatedReviewRequestDto(4, "updated content here");

            // when
            reviewService.updateReview(reviewId, request, userId);

            // then
            ArgumentCaptor<ReviewEntity> reviewEntityCaptor = ArgumentCaptor.forClass(ReviewEntity.class);
            verify(reviewRepository, times(1)).save(reviewEntityCaptor.capture());
            ReviewEntity savedEntity = reviewEntityCaptor.getValue();

            assertThat(savedEntity.getRating()).isEqualTo(4);
            assertThat(savedEntity.getContent()).isEqualTo("updated content here");
            assertThat(savedEntity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("실패 - 다른 사람의 리뷰는 수정할 수 없다.")
        void updateReview_NotOwner_ThrowsException() {
            // given
            given_리뷰가_존재하지만_사용자소유가_아니다(reviewId, userId);
            UpdatedReviewRequestDto request = createUpdatedReviewRequestDto(4, "updated content");

            // when & then
            assertThatThrownBy(() -> reviewService.updateReview(reviewId, request, userId))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 리뷰는 수정할 수 없다.")
        void updateReview_NotFound_ThrowsException() {
            // given
            given_리뷰를_찾을수_없다(reviewId);
            UpdatedReviewRequestDto request = createUpdatedReviewRequestDto(4, "updated content");

            // when & then
            assertThatThrownBy(() -> reviewService.updateReview(reviewId, request, userId))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }


    @Nested
    @DisplayName("리뷰 삭제 시나리오")
    class DeleteReviewTest {

        private final UUID userId = UUID.randomUUID();
        private final UUID reviewId = UUID.randomUUID();

        @Test
        @DisplayName("성공 - 이미지 있는 자신의 리뷰를 삭제한다.")
        void deleteReview_Success_WithImage() {
            // given
            given_리뷰가_존재하며_사용자소유이다(reviewId, userId);
            when(reviewImageRepository.findByReviewId(reviewId)).thenReturn(Optional.of(ReviewImageEntity.builder().build()));

            // when
            reviewService.deleteReview(reviewId, userId);

            // then
            verify(reviewRepository, times(1)).delete(any(ReviewEntity.class));
            verify(reviewImageRepository, times(1)).delete(any(ReviewImageEntity.class));
        }

        @Test
        @DisplayName("성공 - 이미지 없는 자신의 리뷰를 삭제한다.")
        void deleteReview_Success_WithoutImage() {
            // given
            given_리뷰가_존재하며_사용자소유이다(reviewId, userId);
            when(reviewImageRepository.findByReviewId(reviewId)).thenReturn(Optional.empty());

            // when
            reviewService.deleteReview(reviewId, userId);

            // then
            verify(reviewRepository, times(1)).delete(any(ReviewEntity.class));
            verify(reviewImageRepository, never()).delete(any(ReviewImageEntity.class));
        }

        @Test
        @DisplayName("실패 - 다른 사람의 리뷰는 삭제할 수 없다.")
        void deleteReview_NotOwner_ThrowsException() {
            // given
            given_리뷰가_존재하지만_사용자소유가_아니다(reviewId, userId);

            // when & then
            assertThatThrownBy(() -> reviewService.deleteReview(reviewId, userId))
                    .isInstanceOf(ResponseStatusException.class);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 리뷰는 삭제할 수 없다.")
        void deleteReview_NotFound_ThrowsException() {
            // given
            given_리뷰를_찾을수_없다(reviewId);

            // when & then
            assertThatThrownBy(() -> reviewService.deleteReview(reviewId, userId))
                    .isInstanceOf(ResponseStatusException.class);
        }
    }
}