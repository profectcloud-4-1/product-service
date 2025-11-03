package profect.group1.goormdotcom.presigned.config;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@RequiredArgsConstructor
public class S3ClientConfig {
    private final S3Properties s3Properties;

    @Bean
    public S3Client s3Client() {
        // secret key, access key를 yml파일에서 명시적으로 가져오기 위함
        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
                s3Properties.getS3().getAccessKey(),
                s3Properties.getS3().getSecretKey()
        );


        return S3Client.builder()
                .region(Region.of(s3Properties.getS3().getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();


    }

    @Bean
    public S3Presigner s3Presigner() {
        // secret key, access key를 yml파일에서 명시적으로 가져오기 위함

        AwsBasicCredentials awsCreds = AwsBasicCredentials.create(
                s3Properties.getS3().getAccessKey(),
                s3Properties.getS3().getSecretKey()
        );

        return S3Presigner.builder()
                .region(Region.of(s3Properties.getS3().getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
                .build();

    }
}
