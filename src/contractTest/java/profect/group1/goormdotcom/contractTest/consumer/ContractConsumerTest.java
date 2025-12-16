package profect.group1.goormdotcom.contractTest.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@AutoConfigureStubRunner(
        ids = "profect.group1:goormdotcom:0.0.1-SNAPSHOT:stubs:18080",
        stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
public class ContractConsumerTest {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void stubServerIsRunning() {
        String baseUrl = "http://localhost:18080/ping";
        ResponseEntity<String> response = new RestTemplate().getForEntity(baseUrl, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void wiremock_admin_endpoint_is_running() {
        // given
        String adminUrl = "http://localhost:18080/__admin/mappings";
        RestTemplate restTemplate = new RestTemplate();

        // when
        ResponseEntity<String> response =
                restTemplate.getForEntity(adminUrl, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void product_api_response_schema_is_valid() throws Exception {
        // given
        String productId = UUID.randomUUID().toString();
        String url = "http://localhost:18080/api/v1/product/" + productId;

        // when
        ResponseEntity<String> response =
                restTemplate.getForEntity(url, String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode root = objectMapper.readTree(response.getBody());

        // === 최상위 스키마 ===
        assertThat(root.has("code")).isTrue();
        assertThat(root.has("message")).isTrue();
        assertThat(root.has("result")).isTrue();

        JsonNode result = root.get("result");

        // === ProductResponseDto 스키마 ===
        assertThat(result.has("name")).isTrue();
        assertThat(result.has("brandId")).isTrue();
        assertThat(result.has("categoryId")).isTrue();
        assertThat(result.has("description")).isTrue();
        assertThat(result.has("price")).isTrue();
        assertThat(result.has("imageIds")).isTrue();

        // === 타입 검증 ===
        assertThat(result.get("name").isTextual()).isTrue();
        assertThat(result.get("brandId").isTextual()).isTrue();
        assertThat(result.get("categoryId").isTextual()).isTrue();
        assertThat(result.get("description").isTextual()).isTrue();
        assertThat(result.get("price").isNumber()).isTrue();
    }
}
