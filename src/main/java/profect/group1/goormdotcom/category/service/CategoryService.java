package profect.group1.goormdotcom.category.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import profect.group1.goormdotcom.category.domain.Category;
import profect.group1.goormdotcom.category.domain.CategoryTree;
import profect.group1.goormdotcom.category.repository.CategoryRepository;
import profect.group1.goormdotcom.category.repository.entity.CategoryEntity;
import profect.group1.goormdotcom.category.repository.mapper.CategoryMapper;
import profect.group1.goormdotcom.common.apiPayload.code.status.ErrorStatus;
import profect.group1.goormdotcom.common.apiPayload.exceptions.handler.CategoryHandler;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    private static final UUID ROOT_ID =
        UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Transactional
    public UUID createCategory(String name, UUID parentId) {
        // 루트카테고리 없으면 생성
        if (parentId.equals(ROOT_ID)) {
            Optional<CategoryEntity> rootCategory =categoryRepository.findById(ROOT_ID);
            if (!rootCategory.isPresent()) 
                categoryRepository.save(new CategoryEntity(ROOT_ID, null, "ROOT"));   
        }
        
        //부모 카테고리가 존재하는지 확인
        CategoryEntity categoryEntity = categoryRepository.findById(parentId)
                .orElseThrow(() -> new CategoryHandler(ErrorStatus.PARENT_CATEGORY_NOT_FOUND));

        //부모 카테고리와 동일한 카테고리명인지 확인
        if (name.equals(categoryEntity.getName())) {
            throw new CategoryHandler(ErrorStatus.CATEGORY_NAME_DUPLICATED);
        }

        CategoryEntity savedCategoryEntity = categoryRepository.save(
                new CategoryEntity(UUID.randomUUID(), parentId, name)
        );

        return savedCategoryEntity.getId();
    }

    @Transactional
    public Category updateCategory(UUID id, UUID parentId, String name) {
        CategoryEntity entity = categoryRepository.findById(id)
            .orElseThrow(() -> { throw new IllegalArgumentException("Category not found");});

        categoryRepository.findById(parentId)
                .orElseThrow(() -> { throw new IllegalArgumentException("Parent category not found");});

        Category category = CategoryMapper.toDomain(entity);
        category.updateName(name);

        categoryRepository.save(CategoryMapper.toEntity(category));

        return category;
    }

    public void deleteCategory(UUID id) {
        categoryRepository.findById(id)
                .orElseThrow(() -> { throw new IllegalArgumentException("Category not found");});

        categoryRepository.deleteById(id);
    }
}
