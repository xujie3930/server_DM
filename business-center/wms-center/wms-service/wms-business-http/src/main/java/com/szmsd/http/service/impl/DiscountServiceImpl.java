package com.szmsd.http.service.impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.utils.HttpResponseBody;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.common.core.web.page.PageVO;
import com.szmsd.http.config.HttpConfig;
import com.szmsd.http.dto.OperationRecordDto;
import com.szmsd.http.dto.discount.*;
import com.szmsd.http.service.IHttpDiscountService;
import com.szmsd.http.service.http.SaaSPricedRequest;
import com.szmsd.http.util.HttpResponseVOUtils;
import com.szmsd.http.dto.discount.DiscountPage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DiscountServiceImpl extends SaaSPricedRequest implements IHttpDiscountService {

    @Value("${thread.carrierTimes}")
    private int carrierTimes;

    public DiscountServiceImpl(HttpConfig httpConfig) {
        super(httpConfig);
    }


    @Override
    public R<DiscountMainDto> detailResult(String id) {

        log.info("开始访问 discount.detailResult : {} ",id);

        long current = System.currentTimeMillis();

        R<DiscountMainDto> r = HttpResponseVOUtils.transformation(httpGetBody("", "discount.detailResult", carrierTimes,null, id), DiscountMainDto.class);
        long end = System.currentTimeMillis();
        long su = current - end;
        log.info("结束访问 discount.detailResult : {},{} ",id,r.getCode());
        log.info("结束执行时间 : {} 毫秒",su);
        if(r.getCode() == 200 && r.getData() != null && r.getData().getPricingDiscountRules() != null){

            log.info("discount.detailResult : {}",r.getData());

            for(DiscountDetailDto dto: r.getData().getPricingDiscountRules()){
                if(dto.getPackageLimit() != null) {
                    if (dto.getPackageLimit().getMinPackingLimit() != null) {
                        CommonPackingLimit minPackingLimit = dto.getPackageLimit().getMinPackingLimit();
                        dto.getPackageLimit().setMinPackingLimitStr(minPackingLimit.getLength() + "*" + minPackingLimit.getWidth() + "*" + minPackingLimit.getHeight());
                    }
                    if (dto.getPackageLimit().getPackingLimit() != null) {
                        CommonPackingLimit packingLimit = dto.getPackageLimit().getPackingLimit();
                        dto.getPackageLimit().setPackingLimitStr(packingLimit.getLength() + "*" + packingLimit.getWidth() + "*" + packingLimit.getHeight());
                    }
                }
            }

        }


        return r;
    }

    @Override
    public R<PageVO<DiscountDetailDto>> detailResultPage(DiscountPage discountPage) {

        if(discountPage == null){
            return R.failed("参数异常");
        }

        String id = discountPage.getId();

        if(StringUtils.isEmpty(id)){
            return R.failed("折扣ID不允许为空");
        }

        Integer pageNumber = discountPage.getPageNumber();
        Integer pageSize = discountPage.getPageSize();

        R<DiscountMainDto> discountMainDtoR = this.detailResult(id);

        if(discountMainDtoR.getCode() != 200){
            return R.failed(discountMainDtoR.getMsg());
        }

        DiscountMainDto discountMainDto = discountMainDtoR.getData();

        List<DiscountDetailDto> discountDetailDtos = discountMainDto.getPricingDiscountRules();

        if(CollectionUtils.isEmpty(discountDetailDtos)){
            return R.ok(PageVO.empty());
        }

        Integer totalRecords = discountDetailDtos.size();
        Integer pages = totalRecords % pageSize == 0 ? totalRecords / pageSize: totalRecords / pageSize+ 1 ;

        List<DiscountDetailDto> pageDiscount = discountDetailDtos.stream().skip((pageNumber-1)*pageSize).limit(pageSize).
                collect(Collectors.toList());

        PageVO<DiscountDetailDto> pageVO = new PageVO<>();
        pageVO.setPageNumber(pageNumber);
        pageVO.setData(pageDiscount);
        pageVO.setPageSize(pageSize);
        pageVO.setTotalPages(pages);
        pageVO.setTotalRecords(totalRecords);

        return R.ok(pageVO);
    }

    @Override
    public R<PageVO> page(DiscountPageRequest pageDTO) {
        HttpResponseBody hrb = httpPostBody("", "discount.page", pageDTO);
        if (HttpStatus.SC_OK == hrb.getStatus()) {
            PageVO<DiscountMainDto> pageVO = JSON.parseObject(HttpResponseVOUtils.newBody(hrb.getBody()), new TypeReference<PageVO<DiscountMainDto>>(){});
            for(DiscountMainDto main: pageVO.getData()){
                if(main.getPricingDiscountRules() != null){
                    for(DiscountDetailDto dto: main.getPricingDiscountRules()){
                        if(dto.getPackageLimit() != null){
                            if(dto.getPackageLimit().getMinPackingLimit() != null){
                                CommonPackingLimit minPackingLimit = dto.getPackageLimit().getMinPackingLimit();
                                dto.getPackageLimit().setMinPackingLimitStr(minPackingLimit.getLength() + "*" + minPackingLimit.getWidth() + "*" + minPackingLimit.getHeight());
                            }
                            if (dto.getPackageLimit().getPackingLimit() != null) {
                                CommonPackingLimit packingLimit = dto.getPackageLimit().getPackingLimit();
                                dto.getPackageLimit().setPackingLimitStr(packingLimit.getLength() + "*" + packingLimit.getWidth() + "*" + packingLimit.getHeight());
                            }
                        }
                    }

                }
            }

            return R.ok(pageVO);
        }else{
            return R.failed(HttpResponseVOUtils.getErrorMsg(hrb));

        }
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

    @Override
    public R<OperationRecordDto> operationRecord(String id) {
        return HttpResponseVOUtils.transformation(httpGetBody("", "discount.operationRecord", null, id), OperationRecordDto.class);
    }
}
