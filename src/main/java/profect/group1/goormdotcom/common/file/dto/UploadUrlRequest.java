package profect.group1.goormdotcom.common.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import profect.group1.goormdotcom.common.file.domain.FileDomain;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UploadUrlRequest {
    @NotBlank(message = "파일명은 필수입니다")
    private String filename;

    @NotNull(message = "도메인은 필수입니다")
    private FileDomain domain;

    @NotBlank(message = "Content-Type은 필수입니다")
    private String contentType;
}