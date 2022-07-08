package com.szmsd.http.service.impl;


import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.web.page.PageVO;
import com.szmsd.http.config.HttpConfig;
import com.szmsd.http.dto.discount.*;
import com.szmsd.http.dto.discount.*;
import com.szmsd.http.service.IHttpDiscountService;
import com.szmsd.http.service.http.SaaSPricedRequest;
import com.szmsd.http.util.HttpResponseVOUtils;
import org.springframework.stereotype.Service;

@Service
public class DiscountServiceImpl extends SaaSPricedRequest implements IHttpDiscountService {

    public DiscountServiceImpl(HttpConfig httpConfig) {
        super(httpConfig);
    }


    @Override
    public R<DiscountMainDto> detailResult(String id) {
        return HttpResponseVOUtils.transformation(httpGetBody("", "discount.detailResult", null, id), DiscountMainDto.class);
    }

    @Override
    public R<PageVO> page(DiscountPageRequest pageDTO) {
        return HttpResponseVOUtils.transformationPage(httpPostBody("", "discount.page", pageDTO), DiscountMainDto.class);
    }

    @Override
    public R detailImport(UpdateDiscountDetailDto dto) {
        return HttpResponseVOUtils.transformation(httpPutBody("", "discount.detailImport", dto, dto.getTemplateId()));
    }

    @Override
    public R customUpdate(UpdateDiscountCustomDto dto) {
        return HttpResponseVOUtils.transformation(httpPutBody("", "discount.customUpdate", dto, dto.getTemplateId()));
    }

    @Override
    public R create(MergeDiscountDto dto) {
        return HttpResponseVOUtils.transformation(httpPostBody("", "discount.create", dto));
    }

    @Override
    public R update(MergeDiscountDto dto) {
        return HttpResponseVOUtils.transformation(httpPutBody("", "discount.update", dto, dto.getId()));
    }
}
