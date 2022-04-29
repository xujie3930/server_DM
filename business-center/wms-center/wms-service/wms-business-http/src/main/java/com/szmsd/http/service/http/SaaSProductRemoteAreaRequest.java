package com.szmsd.http.service.http;

import com.szmsd.http.config.HttpConfig;
import com.szmsd.http.enums.HttpUrlType;

/**
 * @author zhangyuyuan
 * @date 2021-04-13 16:58
 */
public class SaaSProductRemoteAreaRequest extends AbstractRequest {

    public SaaSProductRemoteAreaRequest(HttpConfig httpConfig) {
        super(httpConfig);
    }

    @Override
    HttpUrlType getHttpUrlType() {
        return HttpUrlType.PRODUCT_REMOTE_AREA;
    }
}
