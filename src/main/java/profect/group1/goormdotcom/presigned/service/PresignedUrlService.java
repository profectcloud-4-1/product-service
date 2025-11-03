package profect.group1.goormdotcom.presigned.service;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import profect.group1.goormdotcom.presigned.config.FileProperties;
import profect.group1.goormdotcom.presigned.config.S3Properties;
import profect.group1.goormdotcom.presigned.controller.dto.PresignedUrlResponse;
import profect.group1.goormdotcom.presigned.domain.FileDomain;
import profect.group1.goormdotcom.presigned.domain.FileStatus;
import profect.group1.goormdotcom.presigned.repository.FileUploadRepository;
import profect.group1.goormdotcom.presigned.repository.entity.FileUpload;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresignedUrlService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;
    private final FileProperties fileProperties;
    private final FileUploadRepository fileUploadRepository;

    /**
     * 업로드용 Presigned URL 생성
     */
    @Transactional
    public PresignedUrlResponse generateUploadUrl(String filename, FileDomain domain, String contentType) {
        String extension = extractExtension(filename);
        String uniqueFilename = UUID.randomUUID().toString() + extension;
        String objectKey = String.format("temp/%s/%s", domain.getPath(), uniqueFilename);

        FileUpload fileUpload = FileUpload.builder()
                .objectKey(objectKey)
                .domain(domain)
                .status(FileStatus.TEMP)
                .build();
        fileUploadRepository.save(fileUpload);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(s3Properties.getS3().getBucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(fileProperties.getPresignedUrlExpiration()))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        log.info("Generated upload URL for file: {}, objectKey: {}", filename, objectKey);

        return PresignedUrlResponse.builder()
                .fileId(fileUpload.getId())
                .presignedUrl(presignedRequest.url().toString())
                .objectKey(objectKey)
                .expiresIn(fileProperties.getPresignedUrlExpiration())
                .build();
    }

    /**
     * 업로드 확정 (temp -> main 이동)
     */
    @Transactional
    public void confirmUpload(UUID fileId) {
        FileUpload fileUpload = fileUploadRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));

        if (fileUpload.getStatus() != FileStatus.TEMP) {
            log.warn("이미 확정된 파일입니다: {}", fileId);
            return;
        }

        String tempKey = fileUpload.getObjectKey();
        String mainKey = tempKey.replace("temp/", "main/");

        try {
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(s3Properties.getS3().getBucket())
                    .sourceKey(tempKey)
                    .destinationBucket(s3Properties.getS3().getBucket())
                    .destinationKey(mainKey)
                    .build();
            s3Client.copyObject(copyRequest);

            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getS3().getBucket())
                    .key(tempKey)
                    .build();
            s3Client.deleteObject(deleteRequest);

            fileUpload.confirm(mainKey);

            log.info("File confirmed: {} -> {}", tempKey, mainKey);

        } catch (S3Exception e) {
            log.error("S3 파일 이동 실패: {}", tempKey, e);
            throw new RuntimeException("파일 확정에 실패했습니다", e);
        }
    }

    /**
     * objectKey 조회 (CloudFront URL 생성용)
     */
    @Transactional
    public String getObjectKey(UUID fileId) {
        FileUpload fileUpload = fileUploadRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("파일을 찾을 수 없습니다: " + fileId));

        if (fileUpload.getStatus() != FileStatus.CONFIRMED) {
            throw new IllegalStateException("확정되지 않은 파일입니다: " + fileId);
        }

        return fileUpload.getObjectKey();
    }

    /**
     * 지연 삭제 처리
     */
    @Transactional
    public void deleteExpiredTempFiles() {
        LocalDateTime expirationTime = LocalDateTime.now()
                .minusHours(fileProperties.getTempRetentionHours());

        List<FileUpload> expiredFiles = fileUploadRepository.findExpiredTempFiles(
                FileStatus.TEMP, expirationTime);

        log.info("만료된 임시 파일 {}개 삭제 시작", expiredFiles.size());

        for (FileUpload file : expiredFiles) {
            try {
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(s3Properties.getS3().getBucket())
                        .key(file.getObjectKey())
                        .build();
                s3Client.deleteObject(deleteRequest);

                file.markAsDeleted();

                log.info("임시 파일 삭제 완료: {}", file.getObjectKey());

            } catch (S3Exception e) {
                log.error("S3 파일 삭제 실패: {}", file.getObjectKey(), e);
            }
        }
    }

    private String extractExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex);
        }
        return "";
    }

}
