package profect.group1.goormdotcom.common.apiPayload.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import profect.group1.goormdotcom.common.apiPayload.code.BaseErrorCode;
import profect.group1.goormdotcom.common.apiPayload.code.ErrorReasonDTO;

@Getter
@AllArgsConstructor
public class GeneralException extends RuntimeException {
    private BaseErrorCode code;

    public ErrorReasonDTO getErrorReason() {
        return this.code.getReason();
    }

    public ErrorReasonDTO getErrorReasonHttpStatus() {
        return this.code.getReasonHttpStatus();
    }

    @Override
    public String getMessage() {
        return this.code.getReason().getMessage();
    }
}
