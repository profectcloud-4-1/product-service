package profect.group1.goormdotcom.presigned.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import profect.group1.goormdotcom.common.apiPayload.ApiResponse;
import profect.group1.goormdotcom.common.apiPayload.code.status.SuccessStatus;
import profect.group1.goormdotcom.presigned.controller.dto.ObjectKeyResponse;
import profect.group1.goormdotcom.presigned.controller.dto.PresignedUrlResponse;
import profect.group1.goormdotcom.presigned.controller.dto.UploadUrlRequest;
import profect.group1.goormdotcom.presigned.service.PresignedUrlService;

import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class PresignedUrlController implements  PresignedUrlApiDocs {
    private final PresignedUrlService presignedUrlService;

    /**
     * 파일 업로드용 Presigned URL 발급
     */
    @PostMapping("/upload-url")
    public ApiResponse<PresignedUrlResponse> generateUploadUrl(
            @Valid @RequestBody UploadUrlRequest request
    ) {
        PresignedUrlResponse response = presignedUrlService.generateUploadUrl(
                request.getFilename(),
                request.getDomain(),
                request.getContentType()
        );
        return ApiResponse.of(SuccessStatus._OK, response);
    }

    /**
     * 파일 업로드 확정 (temp -> main)
     */
    @PostMapping("/{fileId}/confirm")
    public ApiResponse<String> confirmUpload(@PathVariable UUID fileId) {
        presignedUrlService.confirmUpload(fileId);
        return ApiResponse.of(SuccessStatus._OK, "confirmed");
    }

    /**
     * 파일 URL 조회 (CloudFront)
     */
    @GetMapping("/{fileId}/url")
    public ApiResponse<ObjectKeyResponse> getObjectKey(@PathVariable UUID fileId) {
        String url = presignedUrlService.getObjectKey(fileId);
        return ApiResponse.of(SuccessStatus._OK, new ObjectKeyResponse(url));
    }
}


