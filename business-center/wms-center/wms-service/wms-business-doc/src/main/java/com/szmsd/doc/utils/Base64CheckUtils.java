package com.szmsd.doc.utils;

import com.szmsd.common.core.exception.com.CommonException;
import com.szmsd.doc.api.AssertUtil400;
import org.springframework.util.Base64Utils;

/**
 * @ClassName: Base64CheckUtils
 * @Description: base64
 * @Author: 11
 * @Date: 2021-09-17 14:33
 */
public final class Base64CheckUtils extends Base64Utils {

    public static byte[] checkAndConvert(String productImageBase64) {
        byte[] bytes = new byte[0];
        try {
            bytes = Base64CheckUtils.decodeFromString(productImageBase64);
        } catch (Exception e) {
            e.printStackTrace();
            throw new CommonException("400", "Base64文件解析异常!");
        }
        AssertUtil400.isTrue(bytes.length <= 10 * 1024 * 1024, "文件最大仅支持10M");
        return bytes;
    }

}
