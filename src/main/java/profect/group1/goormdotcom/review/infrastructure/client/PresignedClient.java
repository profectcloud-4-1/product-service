package profect.group1.goormdotcom.review.infrastructure.client;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import profect.group1.goormdotcom.review.infrastructure.client.dto.ObjectKeyResponseDto;

import java.util.UUID;
@FeignClient(
        name = "review-to-presigned",
        url = "${service.order.url}"
)
public interface PresignedClient {

    @PostMapping("/api/files/{fileId}/confirm")
    ResponseEntity<Void> confirmUpload(@PathVariable("fileId") UUID fileId);

    @GetMapping("/api/files/{fileId}/url")
    public ResponseEntity<ObjectKeyResponseDto> getObjectKey(@PathVariable UUID fileId);
}