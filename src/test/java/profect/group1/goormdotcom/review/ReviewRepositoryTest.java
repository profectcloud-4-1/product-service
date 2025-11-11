package profect.group1.goormdotcom.review;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import profect.group1.goormdotcom.review.repository.ReviewImageRepository;
import profect.group1.goormdotcom.review.repository.ReviewRepository;
import profect.group1.goormdotcom.review.repository.entity.ReviewEntity;
import profect.group1.goormdotcom.review.repository.entity.ReviewImageEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
@DisplayName("ReviewRepository 단위 테스트")
public class ReviewRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewImageRepository reviewImageRepository; // ReviewImageRepository 추가

    private ReviewEntity createReviewEntity(UUID userId, UUID productId, UUID orderId, int rating, String content) {
        return new ReviewEntity(
                UUID.randomUUID(),
                userId,
                productId,
                orderId,
                rating,
                content,
                LocalDateTime.now(),
                null,
                null
        );
    }

    // ReviewImageEntity 생성을 위한 헬퍼 메소드 추가
    private ReviewImageEntity createReviewImageEntity(UUID reviewId, UUID fileId) {
        return ReviewImageEntity.builder()
                .id(UUID.randomUUID())
                .reviewId(reviewId)
                .fileId(fileId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("리뷰 생성 및 조회 테스트")
    class SaveAndFindTest {

        @Test
        @DisplayName("성공 - 새로운 리뷰를 저장하고 ID로 조회할 수 있다.")
        void saveAndFindById_Success() {
            // given
            ReviewEntity newReview = createReviewEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5, "contentcontent!");

            // when
            reviewRepository.save(newReview);
            entityManager.flush();
            entityManager.clear();

            // then
            Optional<ReviewEntity> foundReview = reviewRepository.findById(newReview.getId());
            assertThat(foundReview).isPresent();
            assertThat(foundReview.get().getContent()).isEqualTo("contentcontent!");
        }

        @Test
        @DisplayName("성공 - OrderId로 리뷰 존재 여부를 확인할 수 있다.")
        void existsByOrderId_Success() {
            // given
            UUID orderId = UUID.randomUUID();
            ReviewEntity review = createReviewEntity(UUID.randomUUID(), UUID.randomUUID(), orderId, 4, "Good service.");
            entityManager.persist(review);
            entityManager.flush();
            entityManager.clear();

            // when
            boolean exists = reviewRepository.existsByOrderId(orderId);
            boolean notExists = reviewRepository.existsByOrderId(UUID.randomUUID());

            // then
            assertThat(exists).isTrue();
            assertThat(notExists).isFalse();
        }

        @Test
        @DisplayName("성공 - ProductId로 리뷰 목록을 페이징하여 조회할 수 있다.")
        void findByProductId_Pageable_Success() {
            // given
            UUID productId = UUID.randomUUID();
            entityManager.persist(createReviewEntity(UUID.randomUUID(), productId, UUID.randomUUID(), 5, "Review 1"));
            entityManager.persist(createReviewEntity(UUID.randomUUID(), productId, UUID.randomUUID(), 4, "Review 2"));
            entityManager.persist(createReviewEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 3, "Other product review"));
            entityManager.flush();
            entityManager.clear();

            Pageable pageable = PageRequest.of(0, 10);

            // when
            Page<ReviewEntity> reviews = reviewRepository.findByProductId(productId, pageable);

            // then
            assertThat(reviews).hasSize(2);
            assertThat(reviews.getContent().get(0).getProductId()).isEqualTo(productId);
            assertThat(reviews.getContent().get(1).getProductId()).isEqualTo(productId);
        }

        @Test
        @DisplayName("성공 - ProductId로 평균 별점을 조회할 수 있다.")
        void findAverageRatingByProductId_Success() {
            // given
            UUID productId = UUID.randomUUID();
            entityManager.persist(createReviewEntity(UUID.randomUUID(), productId, UUID.randomUUID(), 5, "Review 1"));
            entityManager.persist(createReviewEntity(UUID.randomUUID(), productId, UUID.randomUUID(), 3, "Review 2"));
            entityManager.persist(createReviewEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "Other product review"));
            entityManager.flush();
            entityManager.clear();

            // when
            Double averageRating = reviewRepository.findAverageRatingByProductId(productId);

            // then
            assertThat(averageRating).isEqualTo(4.0);
        }

        @Test
        @DisplayName("성공 - ProductId로 총 리뷰 개수를 조회할 수 있다.")
        void countByProductId_Success() {
            // given
            UUID productId = UUID.randomUUID();
            entityManager.persist(createReviewEntity(UUID.randomUUID(), productId, UUID.randomUUID(), 5, "Review 1"));
            entityManager.persist(createReviewEntity(UUID.randomUUID(), productId, UUID.randomUUID(), 3, "Review 2"));
            entityManager.persist(createReviewEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, "Other product review"));
            entityManager.flush();
            entityManager.clear();

            // when
            long count = reviewRepository.countByProductId(productId);

            // then
            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("리뷰 수정 테스트")
    class UpdateTest {

        @Test
        @DisplayName("성공 - 리뷰 내용을 수정하고 저장하면 반영된다.")
        void updateContent_Success() {
            // given
            ReviewEntity review = createReviewEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5, "Original content");
            entityManager.persist(review);
            entityManager.flush();
            entityManager.clear();

            // when
            ReviewEntity foundReview = entityManager.find(ReviewEntity.class, review.getId());
            foundReview = new ReviewEntity(
                    foundReview.getId(),
                    foundReview.getUserId(),
                    foundReview.getProductId(),
                    foundReview.getOrderId(),
                    foundReview.getRating(),
                    "Updated content", // 내용 변경
                    foundReview.getCreatedAt(),
                    LocalDateTime.now(), // updatedAt 설정
                    foundReview.getDeletedAt()
            );
            reviewRepository.save(foundReview);
            entityManager.flush();
            entityManager.clear();

            // then
            ReviewEntity updatedReview = entityManager.find(ReviewEntity.class, review.getId());
            assertThat(updatedReview.getContent()).isEqualTo("Updated content");
            assertThat(updatedReview.getUpdatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("리뷰 삭제 테스트 (Soft Delete)")
    class DeleteTest {

        @Test
        @DisplayName("성공 - 리뷰를 삭제(soft-delete)하면 더 이상 조회되지 않는다.")
        void delete_SoftDelete_Success() {
            // given
            ReviewEntity review = createReviewEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5, "Content to be deleted");
            entityManager.persist(review);
            entityManager.flush();
            UUID reviewId = review.getId();

            // when
            reviewRepository.deleteById(reviewId);
            entityManager.flush();
            entityManager.clear();

            // then
            assertThat(reviewRepository.findById(reviewId)).isNotPresent();

            // then
            Object deletedAt = entityManager.getEntityManager()
                    .createNativeQuery("SELECT deleted_at FROM p_review WHERE id = :id")
                    .setParameter("id", reviewId)
                    .getSingleResult();

            assertThat(deletedAt).isNotNull();
        }
    }

    @Nested
    @DisplayName("ReviewImageRepository 테스트")
    class ReviewImageRepositoryTest {

        @Test
        @DisplayName("성공 - 새로운 리뷰 이미지를 저장하고 ID로 조회할 수 있다.")
        void saveAndFindById_Success() {
            // given
            UUID reviewId = UUID.randomUUID();
            UUID fileId = UUID.randomUUID();
            ReviewImageEntity newImage = createReviewImageEntity(reviewId, fileId);

            // when
            reviewImageRepository.save(newImage);
            entityManager.flush();
            entityManager.clear();

            // then
            Optional<ReviewImageEntity> foundImage = reviewImageRepository.findById(newImage.getId());
            assertThat(foundImage).isPresent();
            assertThat(foundImage.get().getFileId()).isEqualTo(fileId);
        }

        @Test
        @DisplayName("성공 - ReviewId로 리뷰 이미지를 조회할 수 있다.")
        void findByReviewId_Success() {
            // given
            UUID reviewId = UUID.randomUUID();
            UUID fileId = UUID.randomUUID();
            ReviewImageEntity image = createReviewImageEntity(reviewId, fileId);
            entityManager.persist(image);
            entityManager.flush();
            entityManager.clear();

            // when
            Optional<ReviewImageEntity> foundImage = reviewImageRepository.findByReviewId(reviewId);

            // then
            assertThat(foundImage).isPresent();
            assertThat(foundImage.get().getFileId()).isEqualTo(fileId);
        }

        @Test
        @DisplayName("성공 - 리뷰 이미지를 삭제(soft-delete)하면 더 이상 조회되지 않는다.")
        void delete_SoftDelete_Success() {
            // given
            UUID reviewId = UUID.randomUUID();
            UUID fileId = UUID.randomUUID();
            ReviewImageEntity image = createReviewImageEntity(reviewId, fileId);
            entityManager.persist(image);
            entityManager.flush();
            UUID imageId = image.getId();

            // when
            reviewImageRepository.deleteById(imageId);
            entityManager.flush();
            entityManager.clear();

            // then
            assertThat(reviewImageRepository.findById(imageId)).isNotPresent();

            Object deletedAt = entityManager.getEntityManager()
                    .createNativeQuery("SELECT deleted_at FROM p_review_image WHERE id = :id")
                    .setParameter("id", imageId)
                    .getSingleResult();

            assertThat(deletedAt).isNotNull();
        }
    }
}
