package com.szmsd.finance.factory.abstractFactory;

import com.alibaba.fastjson.JSONObject;
import com.szmsd.common.core.utils.StringUtils;
import com.szmsd.common.core.utils.bean.BeanMapperUtil;
import com.szmsd.finance.domain.AccountBalanceChange;
import com.szmsd.finance.domain.FssDeductionRecord;
import com.szmsd.finance.dto.*;
import com.szmsd.finance.enums.BillEnum;
import com.szmsd.finance.enums.CreditConstant;
import com.szmsd.finance.mapper.AccountBalanceChangeMapper;
import com.szmsd.finance.service.IAccountBalanceService;
import com.szmsd.finance.service.IAccountSerialBillService;
import com.szmsd.finance.service.IDeductionRecordService;
import com.szmsd.finance.service.ISysDictDataService;
import com.szmsd.finance.util.SnowflakeId;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author liulei
 */
@Slf4j
public abstract class AbstractPayFactory {

    public static final TimeUnit unit = TimeUnit.SECONDS;

    public static final long time = 180L;

    @Autowired
    private IAccountBalanceService iAccountBalanceService;

    @Resource
    private AccountBalanceChangeMapper accountBalanceChangeMapper;

    @Autowired
    private ISysDictDataService sysDictDataService;

    @Resource
    private IAccountSerialBillService accountSerialBillService;

    @Resource
    private IDeductionRecordService iDeductionRecordService;
    @Resource
    protected ThreadPoolTaskExecutor financeThreadTaskPool;

    /**
     * 返回null 则不处理 接口幂等
     */
    public abstract Boolean updateBalance(CustPayDTO dto);


    public void recordOpLogAsync(CustPayDTO dto, BigDecimal result) {
        financeThreadTaskPool.execute(()-> this.recordOpLog(dto, result));
    }

    public AccountBalanceChange recordOpLog(CustPayDTO dto, BigDecimal result) {
        AccountBalanceChange accountBalanceChange = new AccountBalanceChange();
        log.info("recordOpLog{}-{}", JSONObject.toJSONString(dto), result);
        BeanUtils.copyProperties(dto, accountBalanceChange);
        if (StringUtils.isEmpty(accountBalanceChange.getCurrencyName())) {
            String currencyName = sysDictDataService.getCurrencyNameByCode(accountBalanceChange.getCurrencyCode());
            accountBalanceChange.setCurrencyName(currencyName);
            dto.setCurrencyName(currencyName);
        }

        accountBalanceChange.setSerialNum(SnowflakeId.getNextId12());
        setOpLogAmount(accountBalanceChange, dto.getAmount());
        accountBalanceChange.setCurrentBalance(result);
        accountBalanceChangeMapper.insert(accountBalanceChange);
        return accountBalanceChange;
    }

    /**
     * 详细使用记录
     *
     * @param custPayDTO
     * @param balanceDTO
     */
    public void recordDetailLogAsync(CustPayDTO custPayDTO, BalanceDTO balanceDTO) {
        financeThreadTaskPool.execute(() -> {
            log.info("添加详细使用记录传参custPayDTO: {} {}", JSONObject.toJSONString(custPayDTO), JSONObject.toJSONString(balanceDTO));
            FssDeductionRecord fssDeductionRecord = new FssDeductionRecord();
            CreditInfoBO creditInfoBO = balanceDTO.getCreditInfoBO();
            int creditType = Integer.parseInt(creditInfoBO.getCreditType());
            fssDeductionRecord.setPayMethod(custPayDTO.getPayMethod().name())
                    .setAmount(custPayDTO.getAmount())
                    .setCreditType(creditType)
                    .setStatus(creditType == CreditConstant.CreditTypeEnum.QUOTA.getValue() ?
                            CreditConstant.CreditBillStatusEnum.CHECKED.getValue() : CreditConstant.CreditBillStatusEnum.DEFAULT.getValue())
                    .setOrderNo(custPayDTO.getNo())
                    .setCusCode(custPayDTO.getCusCode()).setCurrencyCode(custPayDTO.getCurrencyCode())
                    .setActualDeduction(balanceDTO.getActualDeduction()).setCreditUseAmount(balanceDTO.getCreditUseAmount())
                    .setRemainingRepaymentAmount(balanceDTO.getCreditUseAmount()).setRepaymentAmount(BigDecimal.ZERO)
                    .setCreditBeginTime(creditInfoBO.getCreditBeginTime()).setCreditEndTime(creditInfoBO.getCreditEndTime())
            ;
            iDeductionRecordService.save(fssDeductionRecord);
        });
    }

    protected abstract void setOpLogAmount(AccountBalanceChange accountBalanceChange, BigDecimal amount);

    protected BigDecimal getCurrentBalance(String cusCode, String currencyCode) {
        return iAccountBalanceService.getCurrentBalance(cusCode, currencyCode);
    }

    protected void setCurrentBalance(String cusCode, String currencyCode, BigDecimal result) {
        iAccountBalanceService.setCurrentBalance(cusCode, currencyCode, result);
    }

    /**
     * 查询该用户对应币别的余额
     *
     * @param cusCode      客户编码
     * @param currencyCode 币别
     * @return 查询结果
     */
    protected BalanceDTO getBalance(String cusCode, String currencyCode) {
        return iAccountBalanceService.getBalance(cusCode, currencyCode);
    }

    protected void updateCreditStatus(CustPayDTO dto) {
        iAccountBalanceService.updateCreditStatus(dto);
    }

    /**
     * 需要扣减信用额
     *
     * @param cusCode
     * @param currencyCode
     * @param result
     * @param needUpdateCredit 是否需要修改授信额
     */
    protected void setBalance(String cusCode, String currencyCode, BalanceDTO result, boolean needUpdateCredit) {
        iAccountBalanceService.setBalance(cusCode, currencyCode, result, needUpdateCredit);
    }

    protected void setBalance(String cusCode, String currencyCode, BalanceDTO result) {
        iAccountBalanceService.setBalance(cusCode, currencyCode, result, false);
    }

    public abstract BalanceDTO calculateBalance(BalanceDTO oldBalance, BigDecimal changeAmount);

    protected void setHasFreeze(CustPayDTO dto) {
        AccountBalanceChangeDTO accountBalanceChange = new AccountBalanceChangeDTO();
        accountBalanceChange.setNo(dto.getNo());
        accountBalanceChange.setCurrencyCode(dto.getCurrencyCode());
        accountBalanceChange.setOrderType(dto.getOrderType());
        accountBalanceChange.setCusCode(dto.getCusCode());
        accountBalanceChange.setHasFreeze(false);
        accountBalanceChange.setPayMethod(BillEnum.PayMethod.BALANCE_FREEZE); //修改冻结的单
        iAccountBalanceService.updateAccountBalanceChange(accountBalanceChange);
    }

    public void setSerialBillLog(CustPayDTO dto) {
        log.info("setSerialBillLog - {}", JSONObject.toJSONString(dto));
        if (CollectionUtils.isEmpty(dto.getSerialBillInfoList())) {
            log.info("setSerialBillLog() list is empty :{} ", dto);
            AccountSerialBillDTO accountSerialBillDTO = BeanMapperUtil.map(dto, AccountSerialBillDTO.class);
            String paymentName = accountSerialBillDTO.getPayMethod().getPaymentName();
            accountSerialBillDTO.setBusinessCategory(paymentName);
            accountSerialBillDTO.setProductCategory(accountSerialBillDTO.getProductCategory());
            String currencyName = accountSerialBillDTO.getCurrencyName();
            currencyName = currencyName == null ? "" : currencyName;
            accountSerialBillDTO.setChargeCategory(paymentName.concat(currencyName));
            accountSerialBillDTO.setChargeType(paymentName);
            accountSerialBillService.add(accountSerialBillDTO);
            return;
        }
        List<AccountSerialBillDTO> collect = dto.getSerialBillInfoList()
                .stream().map(value -> new AccountSerialBillDTO(dto, value)).collect(Collectors.toList());
        List<AccountSerialBillDTO> distanctList = collect.stream().filter(x -> StringUtils.isNotBlank(x.getChargeCategory()) && "物流基础费".equals(x.getChargeCategory()) && BillEnum.PayMethod.BALANCE_DEDUCTIONS.equals(x.getPayMethod())).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(distanctList) && distanctList.size() > 1) {
            AccountSerialBillDTO accountSerialBillDTO = distanctList.get(0);
            log.info("删除物流操作费{}", JSONObject.toJSONString(accountSerialBillDTO));
            collect.remove(accountSerialBillDTO);
        }
        log.info("扣减操作费删除物流操作费后保存{}", JSONObject.toJSONString(collect));
        accountSerialBillService.saveBatch(collect);
    }

    protected void addForCreditBillAsync(BigDecimal addMoney, String cusCode, String currencyCode) {
        if (addMoney.compareTo(BigDecimal.ZERO) <= 0) return;
        financeThreadTaskPool.submit(() -> iDeductionRecordService.addForCreditBill(addMoney, cusCode, currencyCode));

    }

    protected void rollbackFreeze(BalanceDTO oldBalance, BigDecimal freeze) {
        oldBalance.setFreezeBalance(oldBalance.getFreezeBalance().subtract(freeze));
        oldBalance.setCurrentBalance(oldBalance.getCurrentBalance().add(freeze));
    }
}
