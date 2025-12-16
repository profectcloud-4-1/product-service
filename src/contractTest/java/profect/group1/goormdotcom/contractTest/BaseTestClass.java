package profect.group1.goormdotcom.contractTest;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import profect.group1.goormdotcom.common.file.FileStorageManager;
import profect.group1.goormdotcom.product.controller.external.v1.ProductExternalController;
import profect.group1.goormdotcom.product.domain.Product;
import profect.group1.goormdotcom.product.domain.ProductImage;
import profect.group1.goormdotcom.product.service.ProductListItemService;
import profect.group1.goormdotcom.product.service.ProductService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;

@WebMvcTest(controllers = ProductExternalController.class)
@AutoConfigureMockMvc(addFilters = false) // 보안은 이 테스트의 목적이 아님.
public abstract class BaseTestClass {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;
    @MockitoBean
    FileStorageManager fileStorageManager;
    @MockitoBean
    ProductListItemService productListItemService;

    @BeforeEach
    void setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc);

        UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        ProductImage productImage1 = new ProductImage(
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            productId,
            "https://cdn.example.com/images/img-1.jpg"
        );

        ProductImage productImage2 = new ProductImage(
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                productId,
                "https://cdn.example.com/images/img-2.jpg"
        );

        Product product = new Product(
            productId,
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "테스트 상품",
            "상품 설명",
            10000,
            UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            List.of(productImage1, productImage2)
        );

        given(productService.getProduct(productId)).willReturn(product);
    }
}
