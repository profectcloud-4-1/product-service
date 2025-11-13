package profect.group1.goormdotcom.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import profect.group1.goormdotcom.product.repository.ProductRepository;
import profect.group1.goormdotcom.product.repository.entity.ProductEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
@DisplayName("ProductRepository 테스트")
public class ProductRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    private ProductEntity createProductEntity(String name, int price) {
        return new ProductEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                name,
                price,
                UUID.randomUUID(),
                "Test Description",
                "test-image.jpg"
        );
    }

    @Nested
    @DisplayName("제품 생성 테스트")
    class SaveTest {

        @Test
        @DisplayName("성공 - 새로운 제품을 저장하면 INSERT가 실행된다.")
        void save_NewEntity_PerformsInsert() {
            // given
            ProductEntity newProduct = createProductEntity("Test Product", 10000);

            // when
            ProductEntity savedProduct = productRepository.save(newProduct);

            // then
            assertThat(savedProduct.getId()).isNotNull();
            // entityManager를 통해 DB에 실제로 저장되었는지 확인
            ProductEntity foundInDb = entityManager.find(ProductEntity.class, savedProduct.getId());
            assertThat(foundInDb).isNotNull();
            assertThat(foundInDb.getName()).isEqualTo("Test Product");
        }

        @Test
        @DisplayName("성공 - 기존 제품을 저장하면 UPDATE가 실행된다.")
        void save_ExistingEntity_PerformsUpdate() {
            // given
            ProductEntity originalProduct = createProductEntity("Original Name", 10000);
            entityManager.persist(originalProduct);
            entityManager.flush();
            entityManager.clear();

            // when
            // update 상황 시뮬레이션
            ProductEntity updatedInfo = new ProductEntity(
                    originalProduct.getId(),
                    originalProduct.getBrandId(),
                    originalProduct.getCategoryId(),
                    "Updated Name",
                    15000,
                    UUID.randomUUID(),
                    originalProduct.getDescription(),
                    originalProduct.getMainImageUri()
            );
            productRepository.save(updatedInfo);
            entityManager.flush();
            entityManager.clear();

            // then
            ProductEntity foundAfterUpdate = entityManager.find(ProductEntity.class, originalProduct.getId());
            assertThat(foundAfterUpdate.getName()).isEqualTo("Updated Name");
            assertThat(foundAfterUpdate.getPrice()).isEqualTo(15000);
            assertThat(foundAfterUpdate.getMainImageUri()).isEqualTo(originalProduct.getMainImageUri());
        }
    }

    @Nested
    @DisplayName("제품 조회 테스트")
    class FindTest {

        @Test
        @DisplayName("성공 - ID로 제품을 조회할 수 있다.")
        void findById_Success() {
            // given
            ProductEntity product = createProductEntity("Product", 20000);
            entityManager.persist(product);
            entityManager.flush();
            entityManager.clear();

            // when
            Optional<ProductEntity> foundProduct = productRepository.findById(product.getId());

            // then
            assertThat(foundProduct).isPresent();
            assertThat(foundProduct.get().getId()).isEqualTo(product.getId());
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 ID로 조회하면 Optional.empty를 반환한다.")
        void findById_NotFound_ReturnsEmpty() {
            // given
            UUID nonExistentId = UUID.randomUUID();

            // when
            Optional<ProductEntity> foundProduct = productRepository.findById(nonExistentId);

            // then
            assertThat(foundProduct).isNotPresent();
        }
    }

    @Nested
    @DisplayName("제품 삭제 테스트 (Soft-delete)")
    class DeleteTest {

        @Test
        @DisplayName("성공 - 제품을 삭제하면 findById로 조회되지 않는다.")
        void delete_Success_NotFindable() {
            // given
            ProductEntity product = createProductEntity("Product", 5000);
            entityManager.persist(product);
            entityManager.flush();

            // when
            productRepository.deleteById(product.getId());
            entityManager.flush();
            entityManager.clear();

            // then
            Optional<ProductEntity> foundProduct = productRepository.findById(product.getId());
            assertThat(foundProduct).isNotPresent();
        }

        @Test
        @DisplayName("성공 - 제품을 삭제해도 DB에는 deleted_at이 설정된 채로 남아있다.")
        void delete_Success_RemainsInDBWithDeletedAt() {
            // given
            ProductEntity product = createProductEntity("Product to delete", 5000);
            entityManager.persist(product);
            entityManager.flush();
            UUID productId = product.getId();

            // when
            productRepository.delete(product);
            entityManager.flush();
            entityManager.clear();

            // then
            ProductEntity deletedProduct = (ProductEntity) entityManager.getEntityManager()
                    .createNativeQuery("SELECT * FROM p_product WHERE id = :id", ProductEntity.class)
                    .setParameter("id", productId)
                    .getSingleResult();

            assertThat(deletedProduct).isNotNull()
                    .extracting(ProductEntity::getDeletedAt).isNotNull();
        }
    }

    @Nested
    @DisplayName("제품 전체 조회 테스트")
    class FindAllTest {

        @Test
        @DisplayName("성공 - 삭제되지 않은 모든 제품 목록을 반환한다.")
        void findAll_Success() {
            // given
            ProductEntity product1 = createProductEntity("Product 1", 100);
            ProductEntity product2 = createProductEntity("Product 2", 200);
            ProductEntity deletedProduct = createProductEntity("Deleted Product", 300);

            entityManager.persist(product1);
            entityManager.persist(product2);
            entityManager.persist(deletedProduct);
            entityManager.flush();

            // when
            productRepository.delete(deletedProduct);
            entityManager.flush();
            entityManager.clear();

            List<ProductEntity> products = productRepository.findAll();

            // then
            assertThat(products).hasSize(2);
            assertThat(products).extracting(ProductEntity::getName)
                    .containsExactlyInAnyOrder("Product 1", "Product 2");
        }
    }
}