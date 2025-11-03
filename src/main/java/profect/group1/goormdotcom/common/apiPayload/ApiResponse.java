package profect.group1.goormdotcom.common.apiPayload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import profect.group1.goormdotcom.common.apiPayload.code.BaseCode;
import profect.group1.goormdotcom.common.apiPayload.code.status.SuccessStatus;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"code", "message", "result"})
public class ApiResponse <T> {
    private final String code;
    private final String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T result;

    //성공한 경우 응답
    public static <T> ApiResponse<T> onSuccess(T result) {
        return new ApiResponse<>(SuccessStatus._OK.getCode(), SuccessStatus._OK.getMessage(), result);
    }

    //커스텀 응답
    public static <T> ApiResponse<T> of(BaseCode code, T result) {
        return new ApiResponse<>(code.getReasonHttpStatus().getCode(), code.getReasonHttpStatus().getMessage(), result);
    }

    //실패한 경우 응답
    public static <T> ApiResponse<T> onFailure(String code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

}

