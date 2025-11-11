package profect.group1.goormdotcom.common.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {

    private UUID fileId;
    private String presignedUrl;
    private String objectKey;
    private int expiresIn;
}