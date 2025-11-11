package profect.group1.goormdotcom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest
@ActiveProfiles("test")
class GoormdotcomApplicationTests {

    //전체 컨텍스트 테스트가 아닌 단위 테스트만 진행
    /*
    @Test
	void contextLoads() {
	}
    */
}
