package profect.group1.goormdotcom.common.apiPayload.exceptions.handler;

import profect.group1.goormdotcom.common.apiPayload.code.BaseErrorCode;
import profect.group1.goormdotcom.common.apiPayload.exceptions.GeneralException;

public class CartHandler extends GeneralException {
    public CartHandler(BaseErrorCode errorCode){super(errorCode);}
}
