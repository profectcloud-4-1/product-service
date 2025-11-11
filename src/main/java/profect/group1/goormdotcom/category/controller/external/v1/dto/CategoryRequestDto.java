package profect.group1.goormdotcom.category.controller.external.v1.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CategoryRequestDto(
    @NotBlank(message = "카테고리 이름은 비어 있을 수 없습니다.")
    String name,
    UUID parentId
) {
}