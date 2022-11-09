package com.szmsd.finance.controller;

import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.web.controller.BaseController;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.plugin.annotation.AutoValue;
import com.szmsd.finance.domain.PreRecharge;
import com.szmsd.finance.dto.PreRechargeAuditDTO;
import com.szmsd.finance.dto.PreRechargeAuditVO;
import com.szmsd.finance.dto.PreRechargeDTO;
import com.szmsd.finance.service.IPreRechargeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * @author liulei
 */
@Api(tags = {"汇款充值"})
@RestController
@RequestMapping("/preRecharge")
public class PreRechargeController extends BaseController {
    @Autowired
    IPreRechargeService preRechargeService;


    @AutoValue
    @PreAuthorize("@ss.hasPermi('PreRecharge:listPage')")
    @ApiOperation(value = "分页查询汇款充值信息")
    @GetMapping("/listPage")
    public TableDataInfo listPage(PreRechargeDTO dto) {
        startPage();
        List<PreRecharge> list = preRechargeService.listPage(dto);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('PreRecharge:save')")
    @ApiOperation(value = "汇款充值")
    @PostMapping("/save")
    public R save(@RequestBody PreRechargeDTO dto){
        return preRechargeService.save(dto);
    }

    @PreAuthorize("@ss.hasPermi('PreRecharge:save')")
    @ApiOperation(value = "审核汇款")
    @PostMapping("/audit")
    public R audit(@RequestBody PreRechargeAuditDTO dto){
        return preRechargeService.audit(dto);
    }

    //@PreAuthorize("@ss.hasPermi('PreRecharge:save')")
    @ApiOperation(value = "审核驳回")
    @PostMapping("/reject")
    public R reject(@RequestBody @Valid PreRechargeAuditVO dto){
        return preRechargeService.reject(dto);
    }


}
