package profect.group1.goormdotcom.common.apiPayload.exceptions.handler;

import profect.group1.goormdotcom.common.apiPayload.code.BaseErrorCode;
import profect.group1.goormdotcom.common.apiPayload.exceptions.GeneralException;

public class PaymentHandler extends GeneralException {
    public PaymentHandler(BaseErrorCode errorCode){
        super(errorCode);
    }
}
