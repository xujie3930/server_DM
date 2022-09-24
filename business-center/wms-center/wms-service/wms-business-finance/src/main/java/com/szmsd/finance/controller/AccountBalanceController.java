package com.szmsd.finance.controller;

import com.github.pagehelper.PageInfo;
import com.szmsd.common.core.domain.R;
import com.szmsd.common.core.web.page.TableDataInfo;
import com.szmsd.common.plugin.annotation.AutoValue;
import com.szmsd.finance.domain.AccountBalance;
import com.szmsd.finance.domain.AccountBalanceChange;
import com.szmsd.finance.dto.*;
import com.szmsd.finance.service.IAccountBalanceService;
import com.szmsd.finance.vo.UserCreditInfoVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author liulei
 */
@Api(tags = {"账户余额管理"})
@RestController
@RequestMapping("/accountBalance")
public class AccountBalanceController extends FssBaseController {
    @Autowired
    IAccountBalanceService accountBalanceService;

    @PreAuthorize("@ss.hasPermi('ExchangeRate:listPage')")
    @ApiOperation(value = "分页查询账户余额信息")
    @GetMapping("/listPage")
    @AutoValue
    public R<PageInfo<AccountBalance>> listPage(AccountBalanceDTO dto) {
        String len=getLen();

        return  accountBalanceService.listPage(dto,len);
    }

    @PreAuthorize("@ss.hasPermi('ExchangeRate:list')")
    @ApiOperation(value = "查询账户余额信息")
    @PostMapping("/list")
    public R<List<AccountBalance>> list(@RequestBody AccountBalanceDTO dto) {
        return R.ok(accountBalanceService.listPages(dto));
    }

    @PreAuthorize("@ss.hasPermi('ExchangeRate:recordListPage')")
    @ApiOperation(value = "分页查询账户余额变动")
    @GetMapping("/recordListPage")
    public TableDataInfo recordListPage(AccountBalanceChangeDTO dto) {
        startPage();
        String len=getLen();
        List<AccountBalanceChange> list = accountBalanceService.recordListPage(dto);
        list.forEach(x-> {
            if (len.equals("en")) {
                x.setCurrencyName(x.getCurrencyCode());
            } else if (len.equals("zh")) {
                x.setCurrencyName(x.getCurrencyName());
            }
        });
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('ExchangeRate:preOnlineIncome')")
    @ApiOperation(value = "预充值")
    @PostMapping("/preOnlineIncome")
    public R preOnlineIncome(@RequestBody CustPayDTO dto) {
        return accountBalanceService.preOnlineIncome(dto);
    }

    @PostMapping("/rechargeCallback")
    @ApiOperation(value = "第三方充值接口回调")
    public R rechargeCallback(@RequestBody RechargesCallbackRequestDTO requestDTO){
        return accountBalanceService.rechargeCallback(requestDTO);
    }

    @PreAuthorize("@ss.hasPermi('ExchangeRate:onlineIncome')")
    @ApiOperation(value = "在线充值（仅供测试）")
    @PostMapping("/onlineIncome")
    public R onlineIncome(@RequestBody CustPayDTO dto) {
        return accountBalanceService.onlineIncome(dto);
    }

    @PreAuthorize("@ss.hasPermi('ExchangeRate:offlineIncome')")
    @ApiOperation(value = "线下充值（仅供测试）")
    @PostMapping("/offlineIncome")
    public R offlineIncome(@RequestBody CustPayDTO dto) {
        return accountBalanceService.offlineIncome(dto);
    }

    @PreAuthorize("@ss.hasPermi('ExchangeRate:withdraw')")
    @ApiOperation(value = "提现（仅供测试）")
    @PostMapping("/withdraw")
    public R withdraw(@RequestBody CustPayDTO dto) {
        return accountBalanceService.withdraw(dto);
    }

    @PreAuthorize("@ss.hasPermi('ExchangeRate:balanceExchange')")
    @ApiOperation(value = "余额汇率转换")
    @PostMapping("/balanceExchange")
    public R balanceExchange(@RequestBody CustPayDTO dto) {
        return accountBalanceService.balanceExchange(dto);
    }

    @PreAuthorize("@ss.hasPermi('ExchangeRate:warehouseFeeDeduct')")
    @ApiOperation(value = "仓储费用扣除")
    @PostMapping("/warehouseFeeDeduct")
    public R warehouseFeeDeductions(@RequestBody CustPayDTO dto){
        return accountBalanceService.warehouseFeeDeductions(dto);
    }

    /**
     * 出库时 扣除 基本费用/扣除物料费用 接口
     * @param dto
     * @return
     */
    @PreAuthorize("@ss.hasPermi('ExchangeRate:feeDeductions')")
    @ApiOperation(value = "费用扣除")
    @PostMapping("/feeDeductions")
    public R feeDeductions(@RequestBody CustPayDTO dto){
        return accountBalanceService.feeDeductions(dto);
    }

    @PreAuthorize("@ss.hasPermi('ExchangeRate:freezeBalance')")
    @ApiOperation(value = "冻结余额")
    @PostMapping("/freezeBalance")
    public R freezeBalance(@RequestBody CusFreezeBalanceDTO dto){
        return accountBalanceService.freezeBalance(dto);
    }

    @PreAuthorize("@ss.hasPermi('ExchangeRate:thawBalance')")
    @ApiOperation(value = "解冻余额")
    @PostMapping("/thawBalance")
    public R thawBalance(@RequestBody CusFreezeBalanceDTO dto){
        return accountBalanceService.thawBalance(dto);
    }

    @PreAuthorize("@ss.hasPermi('ExchangeRate:thawBalance')")
    @ApiOperation(value = "修改用户信用额信息")
    @PostMapping("/updateUserCredit")
    public R updateUserCredit(@Validated @RequestBody UserCreditDTO userCreditDTO){
        accountBalanceService.updateUserCredit(userCreditDTO);
        return R.ok();
    }
    @PreAuthorize("@ss.hasPermi('ExchangeRate:thawBalance')")
    @ApiOperation(value = "查询用户信用额信息")
    @GetMapping("/queryUserCredit/{cusCode}")
    public R<List<UserCreditInfoVO>> queryUserCredit(@PathVariable("cusCode") String cusCode){
        return R.ok(accountBalanceService.queryUserCredit(cusCode));
    }


}