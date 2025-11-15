package profect.group1.goormdotcom.product.service.utils;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Getter
@Component
public class ImageUrlGenerator {

    @Value("${aws.cloudfront.domain}")
    protected String cloudfrontDomain;
    @Value("${aws.cloudfront.default-image}")
    protected String defaultImageObjectKey;


    public String generateProductImageUrl(UUID imageId) {
        String baseUrl = cloudfrontDomain.endsWith("/") ? cloudfrontDomain: cloudfrontDomain + '/';
        return baseUrl + "main/product/" + imageId.toString();
    }

    public String generateDefaultImageUrl() {
        String baseUrl = cloudfrontDomain.endsWith("/") ? cloudfrontDomain: cloudfrontDomain + '/';
        return baseUrl + defaultImageObjectKey;
    }
}
