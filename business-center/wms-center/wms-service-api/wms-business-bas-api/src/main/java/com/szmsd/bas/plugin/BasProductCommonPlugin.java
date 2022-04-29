package com.szmsd.bas.plugin;

import com.szmsd.bas.api.service.BaseProductClientService;
import com.szmsd.bas.domain.BaseProduct;
import com.szmsd.bas.dto.BaseProductConditionQueryDto;
import com.szmsd.common.plugin.interfaces.CacheContext;
import com.szmsd.common.plugin.interfaces.CommonPlugin;
import com.szmsd.common.plugin.interfaces.DefaultCommonParameter;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zhangyuyuan
 * @date 2021-03-29 14:39
 */
@Component
public class BasProductCommonPlugin implements CommonPlugin {
    public static final String SUPPORTS = "BasProduct";

    @Autowired
    private BaseProductClientService baseProductClientService;

    @Override
    public String supports() {
        return SUPPORTS;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public Map<Object, Object> handlerValue(List<Object> skus, DefaultCommonParameter cp, CacheContext cacheContext) {
        BaseProductConditionQueryDto conditionQueryDto = new BaseProductConditionQueryDto();
        conditionQueryDto.setSkus((List<String>) (List<?>) skus);
        List<BaseProduct> baseProductList = baseProductClientService.queryProductList(conditionQueryDto);
        if (CollectionUtils.isEmpty(baseProductList)) {
            return Collections.emptyMap();
        }
        Map<Object, Object> map = new HashMap<>();
        for (BaseProduct product : baseProductList) {
            map.put(product.getCode(), product.getProductName());
        }
        return map;
    }
}
