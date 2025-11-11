package profect.group1.goormdotcom.category.service.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import profect.group1.goormdotcom.category.domain.CategoryNode;
import profect.group1.goormdotcom.category.domain.CategoryTree;
import profect.group1.goormdotcom.category.repository.CategoryRepository;
import profect.group1.goormdotcom.category.repository.entity.CategoryEntity;
import profect.group1.goormdotcom.category.service.CategroyTreeService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategroyTreeService 비즈니스 로직 테스트")
class CategroyTreeServiceTest {

    @InjectMocks
    private CategroyTreeService categroyTreeService;

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryEntity createEntity(UUID id, UUID parentId, String name) {
        return new CategoryEntity(id, parentId, name);
    }

    @Nested
    @DisplayName("전체 카테고리 트리 조회 로직 검증")
    class GetAllCategoryTreeTest {

        @Test
        @DisplayName("성공 - 여러 카테고리가 존재할 때, 전체 트리를 올바르게 생성한다.")
        void getAllCategoryTree_Success() {
            // given
            UUID rootId = UUID.fromString("00000000-0000-0000-0000-000000000000");
            CategoryEntity categoryA = createEntity(UUID.randomUUID(), rootId, "테스트 카테고리 A");
            CategoryEntity categoryB = createEntity(UUID.randomUUID(), rootId, "테스트 카테고리 B");
            CategoryEntity subCategoryA1 = createEntity(UUID.randomUUID(), categoryA.getId(), "테스트 하위 카테고리 A1");

            List<CategoryEntity> allEntities = Arrays.asList(categoryA, categoryB, subCategoryA1);
            when(categoryRepository.findAll()).thenReturn(allEntities);

            // when
            CategoryTree categoryTree = categroyTreeService.getAllCategoryTree();

            // then
            assertThat(categoryTree).isNotNull();
            CategoryNode rootNode = categoryTree.root();
            assertThat(rootNode.id()).isEqualTo(rootId);
            assertThat(rootNode.name()).isEqualTo("ROOT");
            assertThat(rootNode.children()).hasSize(2);
            assertThat(rootNode.children()).extracting(CategoryNode::name)
                    .containsExactlyInAnyOrder("테스트 카테고리 A", "테스트 카테고리 B");

            CategoryNode nodeA = rootNode.children().stream()
                    .filter(n -> n.name().equals("테스트 카테고리 A"))
                    .findFirst().orElseThrow();
            assertThat(nodeA.children()).hasSize(1);
            assertThat(nodeA.children().get(0).name()).isEqualTo("테스트 하위 카테고리 A1");
        }

        @Test
        @DisplayName("성공 (경계값) - 카테고리가 없으면, 가상 루트만 포함된 트리를 반환한다.")
        void getAllCategoryTree_NoCategories_ReturnsRootOnly() {
            // given
            when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
            UUID rootId = UUID.fromString("00000000-0000-0000-0000-000000000000");

            // when
            CategoryTree categoryTree = categroyTreeService.getAllCategoryTree();

            // then
            assertThat(categoryTree).isNotNull();
            CategoryNode rootNode = categoryTree.root();
            assertThat(rootNode.id()).isEqualTo(rootId);
            assertThat(rootNode.children()).isEmpty();
        }
    }

    @Nested
    @DisplayName("자식 카테고리 트리 조회 로직 검증")
    class GetChildCategoryTreeTest {

        @Test
        @DisplayName("성공 - 부모와 자식 카테고리가 존재할 때, 서브 트리를 올바르게 생성한다.")
        void getChildCategoryTree_Success() {
            // given
            UUID parentId = UUID.randomUUID();
            CategoryEntity parentEntity = createEntity(parentId, null, "테스트 부모");
            CategoryEntity child1 = createEntity(UUID.randomUUID(), parentId, "테스트 자식 1");
            CategoryEntity child2 = createEntity(UUID.randomUUID(), parentId, "테스트 자식 2");

            when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parentEntity));
            when(categoryRepository.findAllByParentId(parentId)).thenReturn(Arrays.asList(child1, child2));

            // when
            CategoryTree categoryTree = categroyTreeService.getChildCategoryTree(parentId);

            // then
            assertThat(categoryTree).isNotNull();
            CategoryNode rootNode = categoryTree.root();
            assertThat(rootNode.id()).isEqualTo(parentId);
            assertThat(rootNode.name()).isEqualTo("테스트 부모");
            assertThat(rootNode.children()).hasSize(2);
            assertThat(rootNode.children()).extracting(CategoryNode::name)
                    .containsExactlyInAnyOrder("테스트 자식 1", "테스트 자식 2");
        }

        @Test
        @DisplayName("실패 (예외) - 부모 카테고리가 없으면 예외가 발생한다.")
        void getChildCategoryTree_ParentNotFound_ThrowsException() {
            // given
            UUID nonExistentParentId = UUID.randomUUID();
            when(categoryRepository.findById(nonExistentParentId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> categroyTreeService.getChildCategoryTree(nonExistentParentId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Category not found");
        }
    }
}
