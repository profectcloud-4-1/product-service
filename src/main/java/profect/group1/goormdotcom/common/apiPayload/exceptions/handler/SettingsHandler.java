package profect.group1.goormdotcom.common.apiPayload.exceptions.handler;

import profect.group1.goormdotcom.common.apiPayload.code.BaseErrorCode;
import profect.group1.goormdotcom.common.apiPayload.exceptions.GeneralException;

public class SettingsHandler extends GeneralException {
    public SettingsHandler(BaseErrorCode errorCode){
        super(errorCode);
    }
}
