package profect.group1.goormdotcom.common.file.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aws")
@Getter
@Setter
public class S3Properties {
    private S3Config s3;
    private CloudfrontConfig cloudfront;

    @Getter
    @Setter
    public static class S3Config {
        private String bucket;
        private String region;
        private String endpoint;
        private String accessKey;
        private String secretKey;
    }

    @Getter
    @Setter
    public static class CloudfrontConfig {
        private String domain;
    }
}
