package profect.group1.goormdotcom.category.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import profect.group1.goormdotcom.category.controller.external.v1.CategoryExternalController;
import profect.group1.goormdotcom.category.controller.external.v1.dto.CategoryRequestDto;
import profect.group1.goormdotcom.category.controller.external.v1.dto.CategoryResponseDto;
import profect.group1.goormdotcom.category.controller.external.v1.dto.CategoryTreeResponseDto;
import profect.group1.goormdotcom.category.controller.external.v1.mapper.CategoryDtoMapper;
import profect.group1.goormdotcom.category.domain.Category;
import profect.group1.goormdotcom.category.domain.CategoryNode;
import profect.group1.goormdotcom.category.domain.CategoryTree;
import profect.group1.goormdotcom.category.service.CategoryService;
import profect.group1.goormdotcom.category.service.CategroyTreeService;
import profect.group1.goormdotcom.common.apiPayload.code.status.ErrorStatus;
import profect.group1.goormdotcom.common.apiPayload.exceptions.ExceptionAdvice;
import profect.group1.goormdotcom.common.apiPayload.exceptions.GeneralException;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryExternalController 단위 테스트")
class CategoryExternalControllerTest {

    private static final String BASE_URL = "/api/v1/category";

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CategoryExternalController categoryExternalController;

    @Mock
    private CategoryService categoryService;

    @Mock
    private CategroyTreeService categroyTreeService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(categoryExternalController)
                .setControllerAdvice(new ExceptionAdvice()) // 전역 예외 처리기
                .build();
    }

    @Nested
    @DisplayName("카테고리 조회")
    class GetCategoryTest {

        @Test
        @DisplayName("성공 - 전체 카테고리 트리를 조회한다.")
        void getCategory_Success() throws Exception {
            // given
            CategoryNode rootNode = new CategoryNode(UUID.randomUUID(), null, "Root", Collections.emptyList());
            CategoryTree mockTree = new CategoryTree(rootNode, Map.of(rootNode.id(), rootNode));
            CategoryTreeResponseDto mockResponseDto = new CategoryTreeResponseDto(rootNode.id(), rootNode.name(), Collections.emptyList());

            given(categroyTreeService.getAllCategoryTree()).willReturn(mockTree);

            try (MockedStatic<CategoryDtoMapper> mockedMapper = Mockito.mockStatic(CategoryDtoMapper.class)) {
                mockedMapper.when(() -> CategoryDtoMapper.toCategoryTreeDto(any(CategoryNode.class))).thenReturn(mockResponseDto);

                // when & then
                mockMvc.perform(get(BASE_URL))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("COMMON200"))
                        .andExpect(jsonPath("$.result.id").value(rootNode.id().toString()))
                        .andDo(print());
            }
        }

        @Test
        @DisplayName("성공 - 특정 카테고리의 하위 트리를 조회한다.")
        void getChildCategory_Success() throws Exception {
            // given
            UUID parentId = UUID.randomUUID();
            CategoryNode childNode = new CategoryNode(UUID.randomUUID(), parentId, "Child", Collections.emptyList());
            CategoryTree mockTree = new CategoryTree(childNode, Map.of(childNode.id(), childNode));
            CategoryTreeResponseDto mockResponseDto = new CategoryTreeResponseDto(childNode.id(), childNode.name(), Collections.emptyList());

            given(categroyTreeService.getChildCategoryTree(parentId)).willReturn(mockTree);

            try (MockedStatic<CategoryDtoMapper> mockedMapper = Mockito.mockStatic(CategoryDtoMapper.class)) {
                mockedMapper.when(() -> CategoryDtoMapper.toCategoryTreeDto(any(CategoryNode.class))).thenReturn(mockResponseDto);

                // when & then
                mockMvc.perform(get(BASE_URL + "/{categoryId}", parentId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("COMMON200"))
                        .andExpect(jsonPath("$.result.id").value(childNode.id().toString()))
                        .andDo(print());
            }
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 카테고리의 하위 트리를 조회하면 404 Not Found를 반환한다.")
        void getChildCategory_NotFound_Fails() throws Exception {
            // given
            UUID nonExistentId = UUID.randomUUID();
            given(categroyTreeService.getChildCategoryTree(nonExistentId))
                    .willThrow(new GeneralException(ErrorStatus.CATEGORY_NOT_FOUND));

            // when & then
            mockMvc.perform(get(BASE_URL + "/{categoryId}", nonExistentId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CATEGORY404"))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("카테고리 생성 (POST /api/v1/category)")
    class CreateCategoryTest {

        @Test
        @DisplayName("성공 - 올바른 요청 시, 생성된 카테고리 ID와 200 OK를 반환한다.")
        void createCategory_Success() throws Exception {
            // given
            CategoryRequestDto request = new CategoryRequestDto("새 카테고리", null);
            UUID newCategoryId = UUID.randomUUID();

            given(categoryService.createCategory(request.name(), request.parentId()))
                    .willReturn(newCategoryId);

            // when & then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result").value(newCategoryId.toString()))
                    .andDo(print());

            verify(categoryService).createCategory(request.name(), request.parentId());
        }

        @Test
        @DisplayName("실패 (유효성 검사) - 이름이 비어있으면 400 Bad Request를 반환한다.")
        void createCategory_BlankName_Fails() throws Exception {
            // given
            CategoryRequestDto request = new CategoryRequestDto(" ", null); // 공백 이름

            // when & then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorStatus._BAD_REQUEST.getCode()))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("카테고리 수정 (PUT /api/v1/category/{categoryId})")
    class UpdateCategoryTest {

        @Test
        @DisplayName("성공 - 올바른 요청 시, 수정된 카테고리 정보와 200 OK를 반환한다.")
        void updateCategory_Success() throws Exception {
            // given
            UUID categoryId = UUID.randomUUID();
            CategoryRequestDto request = new CategoryRequestDto("수정된 이름", null);

            Category updatedCategory = new Category(categoryId, null, "수정된 이름");
            CategoryResponseDto mockResponseDto =
                    new CategoryResponseDto(categoryId, null, "수정된 이름");

            given(categoryService.updateCategory(
                    eq(categoryId),
                    eq(request.parentId()),
                    eq(request.name())
            )).willReturn(updatedCategory);

            try (MockedStatic<CategoryDtoMapper> mockedMapper =
                         Mockito.mockStatic(CategoryDtoMapper.class)) {

                mockedMapper.when(() -> CategoryDtoMapper.toCategoryDto(any(Category.class)))
                        .thenReturn(mockResponseDto);

                // when & then
                mockMvc.perform(put(BASE_URL + "/{categoryId}", categoryId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.code").value("COMMON200"))
                        .andExpect(jsonPath("$.result.id").value(categoryId.toString()))
                        .andExpect(jsonPath("$.result.name").value("수정된 이름"))
                        .andDo(print());
            }

            verify(categoryService).updateCategory(
                    eq(categoryId),
                    eq(request.parentId()),
                    eq(request.name())
            );
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 카테고리를 수정하면 404 Not Found와 에러 본문을 반환한다.")
        void updateNonExistentCategory_Fails() throws Exception {
            // given
            UUID nonExistentId = UUID.randomUUID();
            CategoryRequestDto request = new CategoryRequestDto("수정된 이름", null);

            given(categoryService.updateCategory(eq(nonExistentId), any(), any()))
                    .willThrow(new GeneralException(ErrorStatus.CATEGORY_NOT_FOUND));

            // when & then
            mockMvc.perform(put(BASE_URL + "/{categoryId}", nonExistentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("CATEGORY404"))
                    .andExpect(jsonPath("$.message").exists())
                    .andDo(print());

            verify(categoryService).updateCategory(eq(nonExistentId), any(), any());
        }

        @Test
        @DisplayName("실패 (유효성 검사) - 이름이 비어있으면 400 Bad Request를 반환한다.")
        void updateCategory_BlankName_Fails() throws Exception {
            // given
            UUID categoryId = UUID.randomUUID();
            CategoryRequestDto request = new CategoryRequestDto("", null); // 빈 이름

            // when & then
            mockMvc.perform(put(BASE_URL + "/{categoryId}", categoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorStatus._BAD_REQUEST.getCode()))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("카테고리 삭제 (DELETE /api/v1/category/{categoryId})")
    class DeleteCategoryTest {

        @Test
        @DisplayName("성공 - 존재하는 카테고리를 삭제하면 200 OK를 반환한다.")
        void deleteCategory_Success() throws Exception {
            // given
            UUID categoryId = UUID.randomUUID();
            doNothing().when(categoryService).deleteCategory(categoryId);

            // when & then
            mockMvc.perform(delete(BASE_URL + "/{categoryId}", categoryId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(jsonPath("$.result").value(categoryId.toString()))
                    .andDo(print());

            verify(categoryService).deleteCategory(categoryId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 카테고리를 삭제하면 404 Not Found와 에러 본문을 반환한다.")
        void deleteNonExistentCategory_Fails() throws Exception {
            // given
            UUID nonExistentId = UUID.randomUUID();
            doThrow(new GeneralException(ErrorStatus.CATEGORY_NOT_FOUND))
                    .when(categoryService).deleteCategory(nonExistentId);

            // when & then
            mockMvc.perform(delete(BASE_URL + "/{categoryId}", nonExistentId))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("CATEGORY404"))
                    .andExpect(jsonPath("$.message").exists())
                    .andDo(print());

            verify(categoryService).deleteCategory(nonExistentId);
        }
    }
}