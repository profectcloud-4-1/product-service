package profect.group1.goormdotcom.category.service.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import profect.group1.goormdotcom.category.repository.CategoryRepository;
import profect.group1.goormdotcom.category.repository.entity.CategoryEntity;
import profect.group1.goormdotcom.category.service.CategoryService;
import profect.group1.goormdotcom.common.apiPayload.code.status.ErrorStatus;
import profect.group1.goormdotcom.common.apiPayload.exceptions.handler.CategoryHandler;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService 테스트")
class CategoryServiceTest {

    @InjectMocks
    private CategoryService categoryService;

    @Mock
    private CategoryRepository categoryRepository;

    @Nested
    @DisplayName("카테고리 생성")
    class CreateCategoryTest {
        @Test
        @DisplayName("부모 존재 + 이름 중복 X → 카테고리 생성 성공")
        void createCategory_success_whenParentExists_andNameNotDuplicated() {
            // given
            UUID parentId = UUID.randomUUID();
            String parentName = "부모";
            String childName = "자식";

            CategoryEntity parent = new CategoryEntity(null, parentName);
            CategoryEntity saved = new CategoryEntity(parentId, childName);

            when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));
            when(categoryRepository.save(any(CategoryEntity.class))).thenReturn(saved);

            // when
            UUID resultId = categoryService.createCategory(childName, parentId);

            // then
            assertThat(resultId).isEqualTo(saved.getId());
            verify(categoryRepository).save(any(CategoryEntity.class));
        }

        @Test
        @DisplayName("부모 없음 → 예외")
        void createCategory_fail_whenParentNotFound() {
            // given
            UUID parentId = UUID.randomUUID();
            when(categoryRepository.findById(parentId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> categoryService.createCategory("부모", parentId))
                    .isInstanceOf(CategoryHandler.class)
                    .extracting("code")
                    .isEqualTo(ErrorStatus.PARENT_CATEGORY_NOT_FOUND);

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("부모 이름과 동일 → 예외")
        void createCategory_fail_whenNameDuplicatedWithParent() {
            // given
            UUID parentId = UUID.randomUUID();
            String duplicatedName = "중복이름";

            CategoryEntity parent = new CategoryEntity(null, duplicatedName);

            when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));

            // when & then
            assertThatThrownBy(() -> categoryService.createCategory(duplicatedName, parentId))
                    .isInstanceOf(CategoryHandler.class)
                    .extracting("code")
                    .isEqualTo(ErrorStatus.CATEGORY_NAME_DUPLICATED);

            verify(categoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("카테고리 수정")
    class UpdateCategoryTest {

        @Test
        @DisplayName("성공 - 카테고리 정보를 수정한다.")
        void updateCategory_Success() {
            // given
            UUID categoryId = UUID.randomUUID();
            UUID parentId = UUID.randomUUID();
            CategoryEntity existingCategory = new CategoryEntity(parentId, "기존 이름");
            CategoryEntity parentCategory = new CategoryEntity(null, "부모 이름");

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existingCategory));
            when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parentCategory));

            // when
            categoryService.updateCategory(categoryId, parentId, "새 이름");

            // then
            verify(categoryRepository, times(1)).save(any(CategoryEntity.class));
        }

        @Test
        @DisplayName("실패 - 수정할 카테고리가 존재하지 않으면 예외가 발생한다.")
        void updateCategory_CategoryNotFound_ThrowsException() {
            // given
            UUID categoryId = UUID.randomUUID();
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> categoryService.updateCategory(categoryId, UUID.randomUUID(), "새 이름"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Category not found");
        }

        @Test
        @DisplayName("실패 - 새로운 부모 카테고리가 존재하지 않으면 예외가 발생한다.")
        void updateCategory_ParentNotFound_ThrowsException() {
            // given
            UUID categoryId = UUID.randomUUID();
            UUID parentId = UUID.randomUUID();
            CategoryEntity existingCategory = new CategoryEntity(UUID.randomUUID(), "기존 이름");

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existingCategory));
            when(categoryRepository.findById(parentId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> categoryService.updateCategory(categoryId, parentId, "새 이름"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Parent category not found");
        }
    }

    @Nested
    @DisplayName("카테고리 삭제")
    class DeleteCategoryTest {

        @Test
        @DisplayName("성공 - 카테고리를 삭제한다.")
        void deleteCategory_Success() {
            // given
            UUID categoryId = UUID.randomUUID();
            CategoryEntity existingCategory = new CategoryEntity(null, "삭제할 카테고리");
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existingCategory));

            // when
            categoryService.deleteCategory(categoryId);

            // then
            verify(categoryRepository, times(1)).deleteById(categoryId);
        }

        @Test
        @DisplayName("실패 - 삭제할 카테고리가 존재하지 않으면 예외가 발생한다.")
        void deleteCategory_NotFound_ThrowsException() {
            // given
            UUID categoryId = UUID.randomUUID();
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> categoryService.deleteCategory(categoryId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Category not found");
        }
    }
}
