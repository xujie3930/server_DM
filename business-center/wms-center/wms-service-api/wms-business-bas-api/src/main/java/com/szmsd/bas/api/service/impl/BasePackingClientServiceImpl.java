package com.szmsd.bas.api.service.impl;

import com.szmsd.bas.api.feign.BasePackingFeignService;
import com.szmsd.bas.api.feign.SyslanresFeignService;
import com.szmsd.bas.api.service.BasePackingClientService;
import com.szmsd.bas.domain.BasePacking;
import com.szmsd.bas.domain.SysLanres;
import com.szmsd.bas.dto.BasePackingConditionQueryDto;
import com.szmsd.bas.dto.BasePackingDto;
import com.szmsd.bas.dto.BaseProductConditionQueryDto;
import com.szmsd.bas.dto.CreatePackingRequest;
import com.szmsd.common.core.domain.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author zhangyuyuan
 * @date 2021-04-06 16:56
 */
@Service
public class BasePackingClientServiceImpl implements BasePackingClientService {

    @Autowired
    private BasePackingFeignService basePackingFeignService;
    @Resource
    private SyslanresFeignService syslanresFeignService;

    @Override
    public List<BasePacking> queryPackingList(BaseProductConditionQueryDto conditionQueryDto) {
        return R.getDataAndException(this.basePackingFeignService.queryPackingList(conditionQueryDto));
    }

    @Override
    public BasePacking queryByCode(BasePackingConditionQueryDto conditionQueryDto) {
        return R.getDataAndException(this.basePackingFeignService.queryByCode(conditionQueryDto));
    }

    @Override
    public R createPackings(CreatePackingRequest createPackingRequest){
        return this.basePackingFeignService.createPackings(createPackingRequest);
    }

    @Override
    public List<BasePackingDto> listParent(BasePackingDto warehouseCode) {
        return R.getDataAndException(this.basePackingFeignService.listParent(warehouseCode));
    }

    @Override
    public List<BasePackingDto> listParent() {
        return R.getDataAndException(this.basePackingFeignService.listParent());
    }

    @Override
    public R selectSysLanresList(SysLanres sysLanres) {
        return syslanresFeignService.lists(sysLanres);
    }
}
