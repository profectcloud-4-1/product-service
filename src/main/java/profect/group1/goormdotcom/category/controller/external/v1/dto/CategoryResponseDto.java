package profect.group1.goormdotcom.category.controller.external.v1.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import profect.group1.goormdotcom.category.domain.Category;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponseDto {

    private UUID id;
    private UUID parentId;
    private String name;

    public static CategoryResponseDto from(Category category) {
        return CategoryResponseDto.builder()
                .id(category.getId())
                .parentId(category.getParentId())
                .name(category.getName())
                .build();
    }
}