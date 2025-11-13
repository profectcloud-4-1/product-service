package profect.group1.goormdotcom.category;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import profect.group1.goormdotcom.category.repository.CategoryRepository;
import profect.group1.goormdotcom.category.repository.entity.CategoryEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("CategoryRepository 테스트")
public class CategoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CategoryRepository categoryRepository;

    private CategoryEntity createAndPersistCategory(UUID parentId, String name) {
        CategoryEntity category = new CategoryEntity(UUID.randomUUID(), parentId, name);
        return entityManager.persist(category);
    }

    @Nested
    @DisplayName("카테고리 생성 테스트")
    class SaveTest {

        @Test
        @DisplayName("성공 - 새로운 루트 카테고리를 저장한다.")
        void saveRootCategory_Success() {
            // given
            CategoryEntity newCategory = new CategoryEntity(UUID.randomUUID(), null, "테스트 카테고리");

            // when
            CategoryEntity savedCategory = categoryRepository.save(newCategory);
            entityManager.flush();
            entityManager.clear();

            // then
            Optional<CategoryEntity> foundCategory = categoryRepository.findById(savedCategory.getId());
            assertThat(foundCategory).isPresent();
            assertThat(foundCategory.get().getId()).isEqualTo(savedCategory.getId());
            assertThat(foundCategory.get().getName()).isEqualTo("테스트 카테고리");
            assertThat(foundCategory.get().getParentId()).isNull();
        }

        @Test
        @DisplayName("성공 - 새로운 자식 카테고리를 저장한다.")
        void saveChildCategory_Success() {
            // given
            CategoryEntity parent = createAndPersistCategory(null, "부모 카테고리");
            CategoryEntity newChild = new CategoryEntity(UUID.randomUUID(), parent.getId(), "자식 카테고리");

            // when
            CategoryEntity savedChild = categoryRepository.save(newChild);
            entityManager.flush();
            entityManager.clear();

            // then
            Optional<CategoryEntity> foundChild = categoryRepository.findById(savedChild.getId());
            assertThat(foundChild).isPresent();
            assertThat(foundChild.get().getParentId()).isEqualTo(parent.getId());
            assertThat(foundChild.get().getName()).isEqualTo("자식 카테고리");
        }
    }

    @Nested
    @DisplayName("카테고리 조회 테스트")
    class FindTest {

        @Test
        @DisplayName("성공 - ID로 카테고리를 조회한다.")
        void findById_Success() {
            // given
            CategoryEntity category = createAndPersistCategory(null, "테스트 카테고리");

            // when
            Optional<CategoryEntity> foundCategory = categoryRepository.findById(category.getId());

            // then
            assertThat(foundCategory).isPresent();
            assertThat(foundCategory.get().getName()).isEqualTo("테스트 카테고리");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 ID로 조회하면 Optional.empty()를 반환한다.")
        void findById_NotFound_ReturnsEmpty() {
            // when
            Optional<CategoryEntity> foundCategory = categoryRepository.findById(UUID.randomUUID());

            // then
            assertThat(foundCategory).isNotPresent();
        }

        @Test
        @DisplayName("성공 - 부모 ID로 자식 카테고리 목록을 조회한다.")
        void findAllByParentId_Success() {
            // given
            CategoryEntity parent = createAndPersistCategory(null, "부모 카테고리");
            createAndPersistCategory(parent.getId(), "자식 카테고리1");
            createAndPersistCategory(parent.getId(), "자식 카테고리2");

            // when
            List<CategoryEntity> children = categoryRepository.findAllByParentId(parent.getId());

            // then
            assertThat(children).hasSize(2);
            assertThat(children).extracting(CategoryEntity::getName).containsExactlyInAnyOrder("자식 카테고리1", "자식 카테고리2");
        }

        @Test
        @DisplayName("성공 - 자식이 없는 부모 ID로 조회하면 빈 리스트를 반환한다.")
        void findAllByParentId_NoChildren_ReturnsEmptyList() {
            // given
            CategoryEntity parent = createAndPersistCategory(null, "부모 카테고리");

            // when
            List<CategoryEntity> children = categoryRepository.findAllByParentId(parent.getId());

            // then
            assertThat(children).isEmpty();
        }
    }

    @Nested
    @DisplayName("카테고리 수정 테스트")
    class UpdateTest {

        @Test
        @DisplayName("성공 - 카테고리 정보를 수정한다.")
        void updateCategory_Success() {
            // given
            CategoryEntity category = createAndPersistCategory(null, "테스트 카테고리");
            entityManager.flush();
            entityManager.clear();

            // when
            CategoryEntity found = categoryRepository.findById(category.getId())
                    .orElseThrow(() -> new IllegalStateException("Category not found"));
            found.updateName("수정 카테고리");
            categoryRepository.save(found);

            entityManager.flush();
            entityManager.clear();

            // then
            Optional<CategoryEntity> foundAfterUpdate = categoryRepository.findById(category.getId());
            assertThat(foundAfterUpdate).isPresent();
            assertThat(foundAfterUpdate.get().getName()).isEqualTo("수정 카테고리");
        }
    }

    @Nested
    @DisplayName("카테고리 삭제 테스트")
    class DeleteTest {

        @Test
        @DisplayName("성공 - 카테고리를 삭제(soft-delete)하면 findById로 조회되지 않는다.")
        void deleteCategory_Success() {
            // given
            CategoryEntity category = createAndPersistCategory(null, "테스트 카테고리");

            // when
            categoryRepository.delete(category);
            entityManager.flush();
            entityManager.clear();

            // then
            Optional<CategoryEntity> foundCategory = categoryRepository.findById(category.getId());
            assertThat(foundCategory).isNotPresent();
        }

        @Test
        @DisplayName("성공 - 삭제된 카테고리는 조회 결과에 포함되지 않는다.")
        void findAllByParentId_ExcludesDeletedChildren() {
            // given
            CategoryEntity parent = createAndPersistCategory(null, "부모 카테고리");
            CategoryEntity childToKeep = createAndPersistCategory(parent.getId(), "자식 카테고리1");
            CategoryEntity childToDelete = createAndPersistCategory(parent.getId(), "자식 카테고리2");

            // when
            categoryRepository.delete(childToDelete);
            entityManager.flush();
            entityManager.clear();

            // then
            List<CategoryEntity> children = categoryRepository.findAllByParentId(parent.getId());
            assertThat(children).hasSize(1);
            assertThat(children.get(0).getName()).isEqualTo("자식 카테고리1");
        }
    }
}